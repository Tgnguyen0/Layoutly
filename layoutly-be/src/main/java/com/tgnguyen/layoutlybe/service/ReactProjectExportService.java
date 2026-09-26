package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ReactProjectExportService {
    private final ReactGeneratorService reactGeneratorService;
    private final CssGeneratorService cssGeneratorService;

    public ReactProjectExportService(ReactGeneratorService reactGeneratorService,
                                     CssGeneratorService cssGeneratorService) {
        this.reactGeneratorService = reactGeneratorService;
        this.cssGeneratorService = cssGeneratorService;
    }

    public byte[] export(DesignNode root, AssetExportService.AssetBundle assets) {
        try {
            Map<String, byte[]> files = buildProject(root, assets);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RuntimeException("Loi khi dong goi React project: " + exception.getMessage(), exception);
        }
    }

    Map<String, byte[]> buildProject(DesignNode root, AssetExportService.AssetBundle assets) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        List<ReactGeneratorService.GeneratedPage> pages = reactGeneratorService.generatePages(root);
        for (ReactGeneratorService.GeneratedPage page : pages) {
            putText(files, "src/pages/" + page.fileName(), page.source());
        }

        Map<String, String> reactAssetPaths = new LinkedHashMap<>();
        assets.cssUrlByNodeId().forEach((nodeId, path) -> reactAssetPaths.put(nodeId, "../" + path));
        putText(files, "src/styles/layoutly.css", cssGeneratorService.generate(root, reactAssetPaths));
        putText(files, "src/lib/responsiveScale.js", responsiveScaleModule());
        putText(files, "src/App.jsx", generateApp(pages));
        putText(files, "src/main.jsx", mainSource());
        putText(files, "index.html", indexSource());
        putText(files, "package.json", packageJson());
        putText(files, "vite.config.js", viteConfig());
        putText(files, "README.md", readme());

        assets.zipAssetByPath().forEach((path, bytes) -> files.put("src/" + path, bytes));
        return files;
    }

    private String generateApp(List<ReactGeneratorService.GeneratedPage> pages) {
        String importsHeader = "import { useEffect } from 'react'\n"
                + "import { applyResponsiveScale } from './lib/responsiveScale.js'\n";

        if (pages.isEmpty()) {
            return importsHeader + "\nexport default function App() {\n"
                    + "  useEffect(() => { applyResponsiveScale() }, [])\n"
                    + "  return <main />\n}\n";
        }

        StringBuilder source = new StringBuilder(importsHeader);
        for (ReactGeneratorService.GeneratedPage page : pages) {
            source.append("import ").append(page.componentName()).append(" from './pages/")
                    .append(page.fileName()).append("'\n");
        }
        source.append("import './styles/layoutly.css'\n\n");
        if (pages.size() == 1) {
            source.append("export default function App() {\n")
                    .append("  useEffect(() => { applyResponsiveScale() }, [])\n")
                    .append("  return <").append(pages.get(0).componentName()).append(" />\n}\n");
            return source.toString();
        }

        source.append("const pages = [\n");
        for (ReactGeneratorService.GeneratedPage page : pages) {
            source.append("  { name: ").append(quote(page.componentName()))
                    .append(", Component: ").append(page.componentName()).append(" },\n");
        }
        source.append("]\n\n");
        source.append("export default function App() {\n");
        source.append("  useEffect(() => { applyResponsiveScale() }, [])\n");
        source.append("  const Page = pages[0].Component\n");
        source.append("  return <Page />\n");
        source.append("}\n");
        return source.toString();
    }

    /**
     * Ban ES module tuong duong voi HtmlGeneratorService.responsiveScript(): co/gian
     * ".figma-canvas" bang transform: scale() de vua chieu rong ".figma-page" tren moi
     * kich thuoc man hinh. Duoc App.jsx goi lai trong useEffect + tu dong lang nghe resize.
     */
    private String responsiveScaleModule() {
        return "export function applyResponsiveScale() {\n"
                + "  var pages = document.querySelectorAll('.figma-page')\n"
                + "  for (var i = 0; i < pages.length; i++) {\n"
                + "    var page = pages[i]\n"
                + "    var canvas = page.querySelector('.figma-canvas')\n"
                + "    if (!canvas) continue\n"
                + "    var designWidth = parseFloat(page.style.getPropertyValue('--figma-width'))\n"
                + "    var designHeight = parseFloat(page.style.getPropertyValue('--figma-height'))\n"
                + "    if (!designWidth) continue\n"
                + "    canvas.style.width = designWidth + 'px'\n"
                + "    if (designHeight) canvas.style.minHeight = designHeight + 'px'\n"
                + "    var available = page.clientWidth || window.innerWidth\n"
                + "    var scale = available > 0 ? Math.min(1, available / designWidth) : 1\n"
                + "    canvas.style.transform = 'scale(' + scale + ')'\n"
                + "    page.style.height = designHeight ? (designHeight * scale) + 'px' : ''\n"
                + "  }\n"
                + "}\n\n"
                + "if (typeof window !== 'undefined') {\n"
                + "  window.addEventListener('resize', applyResponsiveScale)\n"
                + "  window.addEventListener('orientationchange', applyResponsiveScale)\n"
                + "}\n";
    }

    private String mainSource() {
        return "import React from 'react'\n"
                + "import ReactDOM from 'react-dom/client'\n"
                + "import App from './App.jsx'\n\n"
                + "ReactDOM.createRoot(document.getElementById('root')).render(\n"
                + "  <React.StrictMode><App /></React.StrictMode>,\n"
                + ")\n";
    }

    private String indexSource() {
        return "<!doctype html>\n<html lang=\"vi\">\n<head>\n"
                + "  <meta charset=\"UTF-8\" />\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
                + "  <title>Layoutly React Export</title>\n"
                + "</head>\n<body>\n  <div id=\"root\"></div>\n"
                + "  <script type=\"module\" src=\"/src/main.jsx\"></script>\n"
                + "</body>\n</html>\n";
    }

    private String packageJson() {
        return "{\n"
                + "  \"name\": \"layoutly-react-export\",\n"
                + "  \"private\": true,\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"type\": \"module\",\n"
                + "  \"scripts\": { \"dev\": \"vite\", \"build\": \"vite build\" },\n"
                + "  \"dependencies\": { \"@vitejs/plugin-react\": \"^4.3.1\", \"vite\": \"^5.3.4\", \"react\": \"^18.3.1\", \"react-dom\": \"^18.3.1\" },\n"
                + "  \"devDependencies\": {}\n"
                + "}\n";
    }

    private String viteConfig() {
        return "import { defineConfig } from 'vite'\n"
                + "import react from '@vitejs/plugin-react'\n\n"
                + "export default defineConfig({ plugins: [react()] })\n";
    }

    private String readme() {
        return "# Layoutly React Export\n\n"
                + "Generated from Figma by Layoutly.\n\n"
                + "```bash\nnpm install\nnpm run dev\n```\n";
    }

    private void putText(Map<String, byte[]> files, String path, String content) {
        files.put(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
