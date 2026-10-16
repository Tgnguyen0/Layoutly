package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Versioned enrichment sidecar. Raw input and the public/legacy DesignNode stay unchanged. */
@Service
public class RenderDiagnosticsService {
    public record Diagnostic(String nodeId, String code, String reason, String fallback) {}
    public record Report(int schemaVersion, List<Diagnostic> diagnostics) {}
    public Report analyze(String rawJson) {
        try {
            List<Diagnostic> findings = new ArrayList<>();
            walk(new ObjectMapper().readTree(rawJson), findings);
            return new Report(1, List.copyOf(findings));
        } catch (Exception e) {
            return new Report(1, List.of(new Diagnostic(null, "UNREADABLE_SOURCE", "Cannot inspect source JSON", "Keep original source; do not award fidelity")));
        }
    }
    private void walk(JsonNode node, List<Diagnostic> result) {
        if (node == null) return;
        if (node.isObject() && node.has("id") && node.has("type")) {
            String id = node.path("id").asText();
            if ("GRID".equals(node.path("layoutMode").asText())) add(result,id,"GRID_TRACKS_UNSUPPORTED","Grid tracks/spans are absent from the legacy IR","Legacy freeform geometry; grid reflow unavailable");
            if (node.path("itemSpacing").asDouble() < 0 && "WRAP".equals(node.path("layoutWrap").asText())) add(result,id,"WRAPPED_NEGATIVE_SPACING","Overlap across wrapped flex lines is ambiguous","Legacy layout; manual review required");
            if (node.has("characterStyleOverrides") || node.has("styleOverrideTable")) add(result,id,"MIXED_TEXT_UNSUPPORTED","Character style runs are not represented in legacy IR","Base text style only");
            if (node.path("isMask").asBoolean()) add(result,id,"MASK_UNSUPPORTED","Mask geometry is not represented in legacy IR","Clip bounds only; request permitted subtree asset");
            if (node.path("rotation").asDouble() != 0 || node.has("relativeTransform")) add(result,id,"TRANSFORM_UNSUPPORTED","Nested transforms are not emitted","Absolute axis-aligned bounds only");
            if (node.has("componentId") || node.has("componentProperties")) add(result,id,"COMPONENT_METADATA","Instance/variant semantics are not represented","Render instance children as ordinary nodes");
            if (node.has("blendMode") && !List.of("NORMAL","PASS_THROUGH").contains(node.path("blendMode").asText())) add(result,id,"BLEND_UNSUPPORTED","Blend mode is not emitted","Normal compositing");
            if (node.path("style").has("lineHeightUnit") && !"PIXELS".equals(node.path("style").path("lineHeightUnit").asText())) add(result,id,"LINE_HEIGHT_UNIT","Legacy IR stores lineHeightPx only","Use supplied lineHeightPx or browser normal");
            int fills=0;
            for (JsonNode fill:node.path("fills")) {
                if (!fill.path("visible").asBoolean(true)) continue;
                fills++;
                if ("IMAGE".equals(fill.path("type").asText()) && node.path("children").size()>0) add(result,id,"IMAGE_SUBTREE_CHILDREN","Node image exports can already contain children","Legacy renders image and children; check duplicate content");
            }
            if (fills>1) add(result,id,"PAINT_STACK_UNSUPPORTED","Multiple paint layers are not preserved","First supported solid/linear gradient only");
            if (List.of("VECTOR","BOOLEAN_OPERATION","STAR","LINE","REGULAR_POLYGON").contains(node.path("type").asText())) add(result,id,"VECTOR_ASSET_REQUIRED","Vector paths are not emitted as native markup","Use authorized exported asset; missing asset blocks fidelity");
            for(JsonNode effect:node.path("effects")) if(effect.path("visible").asBoolean(true) && !List.of("DROP_SHADOW","INNER_SHADOW").contains(effect.path("type").asText())) add(result,id,"EFFECT_UNSUPPORTED","Effect "+effect.path("type").asText()+" is not emitted","Omit effect; report incomplete rendering");
            for(JsonNode reaction:node.path("interactions")) for(JsonNode action:reaction.path("actions")) if(!List.of("NAVIGATE","SCROLL_TO").contains(action.path("navigation").asText())) add(result,id,"PROTOTYPE_ACTION_UNSUPPORTED","Only node click navigation/scroll are mapped","Do not invent destination");
        }
        if(node.isContainerNode()) for(JsonNode child:node) walk(child,result);
    }
    private void add(List<Diagnostic> result,String id,String code,String reason,String fallback) { result.add(new Diagnostic(id,code,reason,fallback)); }
}
