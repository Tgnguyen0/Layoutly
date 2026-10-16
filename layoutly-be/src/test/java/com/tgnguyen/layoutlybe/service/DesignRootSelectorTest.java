package com.tgnguyen.layoutlybe.service;

import com.tgnguyen.layoutlybe.model.ir.Bounds;
import com.tgnguyen.layoutlybe.model.ir.DesignNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DesignRootSelectorTest {
    private DesignNode frame(String id, String name, double width, double height, DesignNode... children) {
        return DesignNode.builder().id(id).name(name).type("FRAME").bounds(new Bounds(0d, 0d, width, height))
                .children(java.util.List.of(children)).build();
    }
    private DesignNode container(String id, String type, DesignNode... children) {
        return DesignNode.builder().id(id).name(type).type(type).children(java.util.List.of(children)).build();
    }

    @Test void fullFileAndCanvasChooseWholeScreenInsteadOfNearerWidthContent() {
        var content = frame("3:0", "Content", 1462, 3040);
        var screen = frame("2:0", "Travel", 1920, 4600, content);
        var page = container("1:0", "CANVAS", screen);
        var showcase = container("4:0", "CANVAS", frame("5:0", "Open Graph Image", 1200, 630));
        var document = container("0:0", "DOCUMENT", page, showcase);
        for (double width : new double[]{1440, 1280, 768, 390}) {
            assertThat(DesignRootSelector.preview(document, width)).isSameAs(screen);
            assertThat(DesignRootSelector.preview(page, width)).isSameAs(screen);
        }
        assertThat(DesignRootSelector.defaultPage(document)).isSameAs(page);
    }

    @Test void unwrapsPresentationCoverToItsScreen() {
        var screen = frame("2:0", "Desktop Landing", 1440, 1200);
        var cover = frame("1:0", "Cover", 77001, 9276, screen);
        assertThat(DesignRootSelector.preview(container("0:0", "CANVAS", cover), 1440)).isSameAs(screen);
    }

    @Test void viewportSelectsAmongSiblingScreensWithoutEnteringSections() {
        var desktop = frame("2:0", "Desktop", 1440, 1600);
        var mobile = frame("3:0", "Mobile", 390, 1200);
        var page = container("1:0", "CANVAS", desktop, mobile);
        assertThat(DesignRootSelector.preview(page, 1440)).isSameAs(desktop);
        assertThat(DesignRootSelector.preview(page, 390)).isSameAs(mobile);
    }

    @Test void keepsSmallFixturesAndUnboundedContainersUsable() {
        var row = frame("2:0", "Horizontal Row", 600, 100);
        assertThat(DesignRootSelector.preview(container("1:0", "CANVAS", row), 1440)).isSameAs(row);
        var empty = container("0:0", "DOCUMENT");
        assertThat(DesignRootSelector.preview(empty, 1440)).isSameAs(empty);
        assertThat(DesignRootSelector.preview(null, 1440)).isNull();
    }

    @Test void adjacentHeaderHeroAndContentFramesAreOnePage() {
        var header = frame("1", "Header", 1920, 100);
        var hero = frame("2", "Hero", 1920, 1200).toBuilder().bounds(new Bounds(0d, 100d, 1920d, 1200d)).build();
        var content = frame("3", "Content", 1920, 1098).toBuilder().bounds(new Bounds(0d, 1300d, 1920d, 1098d)).build();
        var page = container("canvas", "CANVAS", header, hero, content);
        assertThat(DesignRootSelector.continuousSections(page)).isTrue();
        assertThat(DesignRootSelector.preview(page, 1440)).isSameAs(page);
        assertThat(new HtmlGeneratorService().generateAuto(page)).hasSize(1);
        var separated = container("canvas", "CANVAS", header, hero.toBuilder().bounds(new Bounds(0d, 200d, 1920d, 1200d)).build());
        assertThat(DesignRootSelector.continuousSections(separated)).isFalse();
    }
}
