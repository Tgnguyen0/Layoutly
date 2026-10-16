package com.tgnguyen.layoutlybe.fixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import com.tgnguyen.layoutlybe.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Read-only smoke of local legacy source data, separate from licensed synthetic corpus. */
@EnabledIfSystemProperty(named="layoutly.legacy.audit.output",matches=".+")
class LegacyRendererAuditTest {
    @Test void reparseExistingRawAndGenerateWithoutWritingUserData() throws Exception {
        var mapper=new ObjectMapper(); List<Map<String,Object>> results=new ArrayList<>();
        Path original=Path.of("layoutly-data/snapshots");
        try(var directories=Files.list(original)) {
            for(Path dir:directories.filter(Files::isDirectory).sorted().toList()) {
                if(!Files.exists(dir.resolve("metadata.json")))continue;
                var metadata=mapper.readTree(dir.resolve("metadata.json").toFile());
                String raw=Files.readString(dir.resolve("figma-raw.json"));
                long start=System.nanoTime();
                DesignNode ir=new FigmaParserService().parseDesignTree(raw);
                DesignNode root=find(ir,metadata.path("rootNodeId").asText());
                if(root==null)root=ir;
                DesignNode preview=DesignRootSelector.preview(root,1440);
                assertThat(new HtmlGeneratorService().generateForNode(preview)).isNotBlank();
                assertThat(new CssGeneratorService().generateForNode(preview,Map.of())).doesNotContain("NaN","Infinity");
                assertThat(new ReactGeneratorService(true).generatePages(DesignRootSelector.defaultPage(root))).isNotEmpty();
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("snapshotId",dir.getFileName().toString()); row.put("nodes",count(ir)); row.put("rootId",root.getId());row.put("previewRootId",preview.getId());
                row.put("parseAndGenerateMs",(System.nanoTime()-start)/1_000_000.0);
                row.put("diagnosticCount",new RenderDiagnosticsService().analyze(raw).diagnostics().size());
                row.put("referenceAvailable",Files.isRegularFile(dir.resolve("reference.png"))); row.put("fidelityScore",null);row.put("scope","source reparse + generation smoke only; no visual correctness claim");
                results.add(row);
            }
        }
        assertThat(results).isNotEmpty();
        Files.writeString(Path.of(System.getProperty("layoutly.legacy.audit.output")),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results),StandardOpenOption.CREATE_NEW);
    }
    private DesignNode find(DesignNode n,String id){if(id.equals(n.getId()))return n;for(DesignNode c:n.getChildren()){DesignNode f=find(c,id);if(f!=null)return f;}return null;}
    private int count(DesignNode n){return 1+n.getChildren().stream().mapToInt(this::count).sum();}
}
