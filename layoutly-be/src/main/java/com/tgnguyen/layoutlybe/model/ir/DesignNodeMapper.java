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
        TransitionSpec transition = node.getTransition() != null ? node.getTransition() : TransitionSpec.none(); // Animation
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
                .assetRenderBounds(node.getAsset() == null ? null : node.getAsset().renderBounds())
                .x(bounds.x())
                .y(bounds.y())
                .width(bounds.width())
                .height(bounds.height())
                .backgroundColor(style.getBackgroundColor())
                .backgroundGradient(style.getBackgroundGradient())
                .borderColor(style.getBorderColor())
                .borderWidth(style.getBorderWidth())
                .opacity(style.getOpacity())
                .cornerRadius(style.getCornerRadius())
                .fontFamily(style.getFontFamily())
                .fontSize(style.getFontSize())
                .fontWeight(style.getFontWeight())
                .lineHeight(style.getLineHeight())
                .letterSpacing(style.getLetterSpacing())
                .textAlign(style.getTextAlign())
                .textAutoResize(style.getTextAutoResize())
                .fontStyle(style.getFontStyle())
                .textCase(style.getTextCase())
                .cornerRadii(style.getCornerRadii())
                .shadows(style.getShadows())
                .clipsContent(style.getClipsContent())
                .visible(style.getVisible())
                .paddingTop(padding.top())
                .paddingRight(padding.right())
                .paddingBottom(padding.bottom())
                .paddingLeft(padding.left())
                .itemSpacing(layout.getGap())
                .linkTargetId(node.getLinkTargetId())
                .transitionType(transition.type()) // Animation
                .transitionDurationMs(transition.durationMs()) // Animation
                .transitionEasing(transition.easing()) // Animation
                .linkTargetId(node.getLinkTargetId())
                .linkNavigation(node.getLinkNavigation())
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
                .role(node.getRole() != null ? node.getRole() : NodeRole.CONTAINER)
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
                        .backgroundGradient(node.getBackgroundGradient())
                        .borderColor(node.getBorderColor())
                        .borderWidth(node.getBorderWidth())
                        .opacity(node.getOpacity())
                        .cornerRadius(node.getCornerRadius())
                        .fontFamily(node.getFontFamily())
                        .fontSize(node.getFontSize())
                        .fontWeight(node.getFontWeight())
                        .lineHeight(node.getLineHeight())
                        .letterSpacing(node.getLetterSpacing())
                        .textAlign(node.getTextAlign())
                        .textAutoResize(node.getTextAutoResize())
                        .fontStyle(node.getFontStyle())
                        .textCase(node.getTextCase())
                        .cornerRadii(node.getCornerRadii())
                        .shadows(node.getShadows())
                        .clipsContent(node.getClipsContent())
                        .visible(node.getVisible())
                        .build())
                .asset(new AssetSpec(node.isExportAsImage(), node.isExportAsImage() ? "RASTERIZED" : null,
                        node.getAssetRenderBounds()))
                .linkTargetId(node.getLinkTargetId())
                .transition(new TransitionSpec(node.getTransitionType(), node.getTransitionDurationMs(), node.getTransitionEasing()))
                .linkTargetId(node.getLinkTargetId())
                .linkNavigation(node.getLinkNavigation())
                .children(children)
                .build();
    }

    private static boolean hasRenderableParentRole(UINode node) {
        return node.getChildren() != null && !node.getChildren().isEmpty()
                && !"DOCUMENT".equals(node.getType()) && !"CANVAS".equals(node.getType());
    }
}
