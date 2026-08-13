package com.tgnguyen.layoutlybe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Response cua GET /v1/files/:key/nodes?ids=...
 * Khac voi /files/:key: cau truc la 1 Map, key la nodeId minh request,
 * value la object boc ngoai chua "document" (chinh la node do).
 *
 * Vi du JSON that:
 * {
 *   "nodes": {
 *     "12:345": { "document": { "id": "12:345", "type": "FRAME", ... } }
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FigmaNodesResponse(
        Map<String, NodeWrapper> nodes
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeWrapper(FigmaNode document) {}
}
