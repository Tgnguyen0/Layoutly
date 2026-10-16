package com.tgnguyen.layoutlybe.service;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class RenderDiagnosticsServiceTest {
    @Test void attachesUnsupportedPropertiesToSourceNodeWithoutChangingSource() {
        String raw="""
          {"document":{"id":"grid","type":"FRAME","layoutMode":"GRID","children":[
           {"id":"text","type":"TEXT","characters":"Tiếng Việt","characterStyleOverrides":[0,1]},
           {"id":"mask","type":"RECTANGLE","isMask":true},
           {"id":"blur","type":"RECTANGLE","effects":[{"type":"LAYER_BLUR","radius":8}]}
          ]}}
          """;
        var report=new RenderDiagnosticsService().analyze(raw);
        assertThat(report.schemaVersion()).isEqualTo(1);
        assertThat(report.diagnostics()).extracting(RenderDiagnosticsService.Diagnostic::nodeId).containsExactly("grid","text","mask","blur");
        assertThat(report.diagnostics()).allSatisfy(d->{assertThat(d.reason()).isNotBlank();assertThat(d.fallback()).isNotBlank();});
        assertThat(raw).contains("Tiếng Việt","characterStyleOverrides");
    }
    @Test void invalidSourceIsExplicitlyIneligibleRatherThanReportedAsSupported() {
        assertThat(new RenderDiagnosticsService().analyze("{").diagnostics()).extracting(RenderDiagnosticsService.Diagnostic::code).containsExactly("UNREADABLE_SOURCE");
    }
}
