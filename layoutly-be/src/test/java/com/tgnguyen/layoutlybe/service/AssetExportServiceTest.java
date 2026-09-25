package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.AssetSpec;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AssetExportServiceTest {
    @Test
    void batchesAllImageNodeIdsIntoOneFigmaRequest() {
        FigmaService figmaService = mock(FigmaService.class);
        when(figmaService.getImages("file-key", "1:1,1:2", "png", "token"))
                .thenReturn(Mono.just("{\"images\":{}}"));
        AssetExportService service = new AssetExportService(figmaService);
        DesignNode root = DesignNode.builder()
                .id("0:0")
                .type("DOCUMENT")
                .children(List.of(
                        DesignNode.builder().id("1:1").type("VECTOR").asset(new AssetSpec(true, "VECTOR")).build(),
                        DesignNode.builder().id("1:2").type("RECTANGLE").asset(new AssetSpec(true, "IMAGE")).build()))
                .build();

        var result = service.exportAssets("file-key", "token", root).block();

        assertThat(result).isNotNull();
        verify(figmaService).getImages("file-key", "1:1,1:2", "png", "token");
    }

    @Test
    void splitsLargeImageNodeListsIntoSafeSequentialRequests() {
        FigmaService figmaService = mock(FigmaService.class);
        when(figmaService.getImages(eq("file-key"), anyString(), eq("png"), eq("token")))
                .thenReturn(Mono.just("{\"images\":{}}"));
        AssetExportService service = new AssetExportService(figmaService);

        List<DesignNode> imageNodes = new ArrayList<>();
        for (int index = 0; index < 205; index++) {
            imageNodes.add(DesignNode.builder()
                    .id("1:" + index)
                    .type("VECTOR")
                    .asset(new AssetSpec(true, "VECTOR"))
                    .build());
        }
        DesignNode root = DesignNode.builder()
                .id("0:0")
                .type("DOCUMENT")
                .children(imageNodes)
                .build();

        assertThat(service.exportAssets("file-key", "token", root).block()).isNotNull();

        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(figmaService, times(3)).getImages(eq("file-key"), ids.capture(), eq("png"), eq("token"));
        assertThat(ids.getAllValues())
                .extracting(value -> value.split(",").length)
                .containsExactly(100, 100, 5);
        assertThat(ids.getAllValues().stream().flatMap(value -> Arrays.stream(value.split(","))))
                .hasSize(205)
                .doesNotHaveDuplicates();
    }
}
