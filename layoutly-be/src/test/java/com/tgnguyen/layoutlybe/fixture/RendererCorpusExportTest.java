package com.tgnguyen.layoutlybe.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in offline measurement exporter; never imports/mutates user snapshots or a database. */
@EnabledIfSystemProperty(named="layoutly.fidelity.output",matches=".+")
class RendererCorpusExportTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void exportActualZipsAndNormalizedInputs() throws Exception {
        Path fixtures = Path.of("../layoutly-fe/tests/fidelity/fixtures").toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("layoutly.fidelity.output")).toAbsolutePath().normalize();
        // Preserve every baseline run. Caller must choose a fresh output path.
        Files.createDirectories(output.getParent()); Files.createDirectory(output);
        List<Map<String,Object>> index = new ArrayList<>();
        for(JsonNode spec:mapper.readTree(fixtures.resolve("manifest.json").toFile())) {
            String raw = Files.readString(fixtures.resolve(spec.path("raw").asText()));
            long start = System.nanoTime();
            DesignNode tree = new FigmaParserService().parseDesignTree(raw);
            DesignNode root = find(tree,spec.path("rootId").asText());
            assertThat(root).as(spec.path("id").asText()).isNotNull();
            double parseMs = ms(start);
            Map<String,String> urls = new LinkedHashMap<>(); Map<String,byte[]> bytes = new LinkedHashMap<>();
            spec.path("assets").fields().forEachRemaining(e -> {
                try { String file="assets/"+e.getValue().asText(); urls.put(e.getKey(),file); bytes.put(file,Files.readAllBytes(fixtures.resolve(spec.path("id").asText()).resolve(e.getValue().asText()))); }
                catch(Exception ex) { throw new RuntimeException(ex); }
            });
            var assets = new AssetExportService.AssetBundle(urls,bytes);
            Path caseDir=output.resolve(spec.path("id").asText()); Files.createDirectory(caseDir);
            Files.writeString(caseDir.resolve("figma-raw.json"),raw);
            mapper.writerWithDefaultPrettyPrinter().writeValue(caseDir.resolve("design-ir.json").toFile(),root);
            mapper.writerWithDefaultPrettyPrinter().writeValue(caseDir.resolve("diagnostics.json").toFile(),new RenderDiagnosticsService().analyze(raw));
            var css = new CssGeneratorService(Boolean.getBoolean("layoutly.fidelity.css-corrections"));
            var html = new HtmlGeneratorService();
            start=System.nanoTime();
            byte[] htmlZip = new HtmlProjectExportService(html,css).export(root,assets,"AUTO");
            double htmlMs=ms(start); Files.write(caseDir.resolve("html.zip"),htmlZip); unzip(htmlZip,caseDir.resolve("html"));
            start=System.nanoTime();
            var react = new ReactGeneratorService(Boolean.getBoolean("layoutly.fidelity.page-consistency"));
            byte[] reactZip = new ReactProjectExportService(react,css).export(root,assets);
            double reactMs=ms(start); Files.write(caseDir.resolve("react.zip"),reactZip); unzip(reactZip,caseDir.resolve("react"));
            Map<String,Object> result=mapper.convertValue(spec,Map.class);
            result.put("sourceSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            result.put("nodes",count(root)); result.put("frames",frames(root));
            result.put("cssCorrections",Boolean.getBoolean("layoutly.fidelity.css-corrections"));
            result.put("pageConsistency",Boolean.getBoolean("layoutly.fidelity.page-consistency"));
            result.put("stagesMs",Map.of("parseAndNormalize",parseMs,"htmlAndZip",htmlMs,"reactAndZip",reactMs));
            result.put("htmlFiles",new ArrayList<>(html.generateAuto(root).keySet()));
            result.put("reactRoutes",react.generatePages(root).stream().map(ReactGeneratorService.GeneratedPage::route).toList());
            index.add(result);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("index.json").toFile(),index);
        assertThat(index.size()).isGreaterThanOrEqualTo(20);
    }
    private DesignNode find(DesignNode n,String id){if(id.equals(n.getId()))return n; for(DesignNode c:n.getChildren()){DesignNode f=find(c,id); if(f!=null)return f;} return null;}
    private int count(DesignNode n){return 1+n.getChildren().stream().mapToInt(this::count).sum();}
    private int frames(DesignNode n){return ("FRAME".equals(n.getType())?1:0)+n.getChildren().stream().mapToInt(this::frames).sum();}
    private double ms(long start){return (System.nanoTime()-start)/1_000_000.0;}
    private void unzip(byte[] zip,Path dest)throws Exception {
        Files.createDirectories(dest);
        try(var input=new ZipInputStream(new ByteArrayInputStream(zip))){
            for(var e=input.getNextEntry();e!=null;e=input.getNextEntry()){
                Path target=dest.resolve(e.getName()).normalize(); if(!target.startsWith(dest))throw new IllegalArgumentException("Unsafe ZIP path");
                if(e.isDirectory())Files.createDirectories(target);else{Files.createDirectories(target.getParent());Files.write(target,input.readAllBytes());}
            }
        }
    }
}
