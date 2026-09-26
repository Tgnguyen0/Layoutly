package com.tgnguyen.layoutlybe.model.ir;

import com.tgnguyen.layoutlybe.model.UINode;

import java.util.ArrayList;
import java.util.List;

public final class DesignNodeMapper {
    private DesignNodeMapper() {
    }

    public static UINode toLegacy(DesignNode node) {
        if (node == null) return null;

        Bounds bounds = node.getBounds() != null ? node.getBounds() : Bounds.empty();
        StyleSpec style = node.getStyle() != null ? node.getStyle() : StyleSpec.builder().build();
        LayoutSpec layout = node.getLayout() != null ? node.getLayout() : LayoutSpec.builder().build();
        EdgeInsets padding = layout.getPadding() != null ? layout.getPadding() : EdgeInsets.empty();
        List<UINode> children = node.getChildren() == null
                ? new ArrayList<>()
                : node.getChildren().stream().map(DesignNodeMapper::toLegacy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        return UINode.builder()
                .id(node.getId())
                .name(node.getName())
                .type(node.getType())
                .role(node.getRole())
                .characters(node.getText())
                .children(children)
                .exportAsImage(node.getAsset() != null && node.getAsset().exportAsImage())
                .x(bounds.x())
                .y(bounds.y())
                .width(bounds.width())
                .height(bounds.height())
                .backgroundColor(style.getBackgroundColor())
                .borderColor(style.getBorderColor())
                .borderWidth(style.getBorderWidth())
                .opacity(style.getOpacity())
                .cornerRadius(style.getCornerRadius())
                .fontFamily(style.getFontFamily())
                .fontSize(style.getFontSize())
                .fontWeight(style.getFontWeight())
                .lineHeight(style.getLineHeight())
                .letterSpacing(style.getLetterSpacing())
                .paddingTop(padding.top())
                .paddingRight(padding.right())
                .paddingBottom(padding.bottom())
                .paddingLeft(padding.left())
                .itemSpacing(layout.getGap())
                .build();
    }

    public static DesignNode fromLegacy(UINode node) {
        if (node == null) return null;

        List<DesignNode> children = node.getChildren() == null
                ? List.of()
                : node.getChildren().stream().map(DesignNodeMapper::fromLegacy).toList();

        return DesignNode.builder()
                .id(node.getId())
                .name(node.getName())
                .type(node.getType())
                .text(node.getCharacters())
                .bounds(new Bounds(node.getX(), node.getY(), node.getWidth(), node.getHeight()))
                .layout(LayoutSpec.builder()
                        .type(hasRenderableParentRole(node) ? LayoutType.ABSOLUTE : LayoutType.NONE)
                        .padding(new EdgeInsets(node.getPaddingTop(), node.getPaddingRight(), node.getPaddingBottom(), node.getPaddingLeft()))
                        .gap(node.getItemSpacing())
                        .build())
                .sizing(SizingSpec.builder()
                        .horizontal(node.getWidth() != null ? SizingMode.FIXED : SizingMode.UNKNOWN)
                        .vertical(node.getHeight() != null ? SizingMode.FIXED : SizingMode.UNKNOWN)
                        .width(node.getWidth())
                        .height(node.getHeight())
                        .build())
                .style(StyleSpec.builder()
                        .backgroundColor(node.getBackgroundColor())
                        .borderColor(node.getBorderColor())
                        .borderWidth(node.getBorderWidth())
                        .opacity(node.getOpacity())
                        .cornerRadius(node.getCornerRadius())
                        .fontFamily(node.getFontFamily())
                        .fontSize(node.getFontSize())
                        .fontWeight(node.getFontWeight())
                        .lineHeight(node.getLineHeight())
                        .letterSpacing(node.getLetterSpacing())
                        .build())
                .asset(new AssetSpec(node.isExportAsImage(), node.isExportAsImage() ? "RASTERIZED" : null))
                .children(children)
                .build();
    }

    private static boolean hasRenderableParentRole(UINode node) {
        return node.getChildren() != null && !node.getChildren().isEmpty()
                && !"DOCUMENT".equals(node.getType()) && !"CANVAS".equals(node.getType());
    }
}
