package com.tgnguyen.layoutlybe.fixture;

import com.tgnguyen.layoutlybe.model.ir.Direction;
import com.tgnguyen.layoutlybe.service.FigmaParserService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureLoaderTest {
    @Test
    void loadsLocalFixtureWithoutFigmaApi() throws Exception {
        FixtureLoader loader = new FixtureLoader();
        var tree = new FigmaParserService().parseDesignTree(loader.loadRawJson("horizontal"));

        assertThat(loader.loadMetadata("horizontal").get("caseName").asText()).isEqualTo("horizontal");
        assertThat(loader.loadDesignIr("horizontal").get("type").asText()).isEqualTo("DOCUMENT");
        assertThat(tree.getChildren().get(0).getChildren().get(0).getLayout().getDirection())
                .isEqualTo(Direction.ROW);
    }
}
