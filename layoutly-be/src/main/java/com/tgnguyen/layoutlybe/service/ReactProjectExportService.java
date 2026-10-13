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

    public Map<String, byte[]> buildProject(DesignNode root, AssetExportService.AssetBundle assets) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        List<ReactGeneratorService.GeneratedPage> pages = reactGeneratorService.generatePages(root);
        for (ReactGeneratorService.GeneratedPage page : pages) {
            putText(files, "src/pages/" + page.fileName(), page.source());
        }

        Map<String, String> reactAssetPaths = new LinkedHashMap<>();
        assets.cssUrlByNodeId().forEach((nodeId, path) -> reactAssetPaths.put(nodeId, "../" + path));
        putText(files, "src/styles/layoutly.css", cssGeneratorService.generate(root, reactAssetPaths));
        putText(files, "src/styles/view-transitions.css", viewTransitionsCss());
        putText(files, "src/lib/TransitionLink.jsx", transitionLinkModule());
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

        if (pages.size() == 1) {
            StringBuilder single = new StringBuilder(importsHeader);
            single.append("import ").append(pages.get(0).componentName()).append(" from './pages/")
                    .append(pages.get(0).fileName()).append("'\n");
            single.append("import './styles/layoutly.css'\n\n");
            single.append("import './styles/view-transitions.css'\n");
            single.append("export default function App() {\n")
                    .append("  useEffect(() => { applyResponsiveScale() }, [])\n")
                    .append("  return <").append(pages.get(0).componentName()).append(" />\n}\n");
            return single.toString();
        }

        // Nhieu trang: dung react-router-dom. Cac <Link to="..."> trong pages/*.jsx tro toi
        // dung cac route khai bao o day (trang dau tien = "/").
        StringBuilder source = new StringBuilder();
        source.append("import { useEffect } from 'react'\n");
        source.append("import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'\n");
        source.append("import { applyResponsiveScale } from './lib/responsiveScale.js'\n");
        for (ReactGeneratorService.GeneratedPage page : pages) {
            source.append("import ").append(page.componentName()).append(" from './pages/")
                    .append(page.fileName()).append("'\n");
        }
        source.append("import './styles/layoutly.css'\n");
        source.append("import './styles/view-transitions.css'\n\n");
        source.append("// Moi lan doi trang: cuon len dau va tinh lai ti le co gian cua .figma-canvas\n");
        source.append("function RouteEffects() {\n");
        source.append("  const { pathname } = useLocation()\n");
        source.append("  useEffect(() => {\n");
        source.append("    window.scrollTo(0, 0)\n");
        source.append("    applyResponsiveScale()\n");
        source.append("  }, [pathname])\n");
        source.append("  return null\n");
        source.append("}\n\n");
        source.append("export default function App() {\n");
        source.append("  return (\n");
        source.append("    <BrowserRouter>\n");
        source.append("      <RouteEffects />\n");
        source.append("      <Routes>\n");
        for (ReactGeneratorService.GeneratedPage page : pages) {
            source.append("        <Route path=").append(quote(page.route()))
                    .append(" element={<").append(page.componentName()).append(" />} />\n");
        }
        source.append("        <Route path=\"*\" element={<Navigate to=\"/\" replace />} />\n");
        source.append("      </Routes>\n");
        source.append("    </BrowserRouter>\n");
        source.append("  )\n");
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
                + "  \"dependencies\": { \"react\": \"^18.3.1\", \"react-dom\": \"^18.3.1\", \"react-router-dom\": \"^6.26.2\" },\n"
                + "  \"devDependencies\": { \"@vitejs/plugin-react\": \"^4.3.1\", \"vite\": \"^5.3.4\" }\n"
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

    private String transitionLinkModule() {
        return """
            import { flushSync } from 'react-dom'
            import { Link, useNavigate } from 'react-router-dom'
            
            const EFFECTS = { PUSH: 'push', MOVE_IN: 'move-in', SLIDE_IN: 'move-in', MOVE_OUT: 'move-out', SLIDE_OUT: 'move-out' }
            
            // Link co animation chuyen trang cua Figma prototype. Trinh duyet khong ho tro
            // View Transitions (hoac nguoi dung bat giam chuyen dong) thi chuyen trang nhu thuong.
            export default function TransitionLink({ to, onClick, ...props }) {
              const navigate = useNavigate()
            
              function handleClick(e) {
                if (onClick) onClick(e)
                if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return
                if (!document.startViewTransition) return
                if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
            
                const data = e.currentTarget.dataset
                e.preventDefault()
                const root = document.documentElement
                root.style.setProperty('--vt-duration', (data.duration || 300) + 'ms')
                root.style.setProperty('--vt-easing', data.easing || 'ease-out')
                root.dataset.vt = EFFECTS[data.transition] || 'fade'
            
                const transition = document.startViewTransition(() => {
                  flushSync(() => navigate(to))
                })
                transition.finished.finally(() => {
                  delete root.dataset.vt
                  root.style.removeProperty('--vt-duration')
                  root.style.removeProperty('--vt-easing')
                })
              }
            
              return <Link to={to} onClick={handleClick} {...props} />
            }
            """;
    }

    private String viewTransitionsCss() {
        return """
            ::view-transition-old(root), ::view-transition-new(root) {
              animation-duration: var(--vt-duration, 300ms);
              animation-timing-function: var(--vt-easing, ease-out);
            }
            
            @keyframes lyt-vt-in  { from { transform: translateX(100%); } to { transform: none; } }
            @keyframes lyt-vt-out { from { transform: none; } to { transform: translateX(-100%); } }
            
            html[data-vt="push"]::view-transition-old(root), html[data-vt="push"]::view-transition-new(root),
            html[data-vt="move-in"]::view-transition-old(root), html[data-vt="move-in"]::view-transition-new(root),
            html[data-vt="move-out"]::view-transition-old(root), html[data-vt="move-out"]::view-transition-new(root) {
              mix-blend-mode: normal;
            }
            html[data-vt="push"]::view-transition-old(root), html[data-vt="move-out"]::view-transition-old(root) {
              animation-name: lyt-vt-out;
            }
            html[data-vt="move-out"]::view-transition-old(root) { z-index: 2; }
            html[data-vt="push"]::view-transition-new(root), html[data-vt="move-in"]::view-transition-new(root) {
              animation-name: lyt-vt-in;
            }
            html[data-vt="move-in"]::view-transition-old(root), html[data-vt="move-out"]::view-transition-new(root) {
              animation: none;
            }
            """;
    }
}