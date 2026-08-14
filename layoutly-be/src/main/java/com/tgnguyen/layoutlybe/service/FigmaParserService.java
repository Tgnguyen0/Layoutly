package com.tgnguyen.layoutlybe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.UINode;
import org.springframework.stereotype.Service;

@Service
public class FigmaParserService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Nhan chuoi JSON tho tu Figma (response cua /files/{fileKey}),
    // duyet vao field "document" va dung lai thanh cay UINode.
    public UINode parseDocumentTree(String rawFigmaJson) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(rawFigmaJson);
        JsonNode documentNode = rootNode.get("document");

        if (documentNode == null) {
            throw new IllegalArgumentException(
                    "JSON khong co field 'document' - phai la response tu endpoint /files/{fileKey}");
        }

        return buildNode(documentNode);
    }

    private UINode buildNode(JsonNode node) {
        UINode uiNode = new UINode();
        uiNode.setId(textOf(node, "id"));
        uiNode.setName(textOf(node, "name"));
        uiNode.setType(textOf(node, "type"));

        JsonNode childrenNode = node.get("children");
        if (childrenNode != null && childrenNode.isArray()) {
            for (JsonNode child : childrenNode) {
                uiNode.getChildren().add(buildNode(child));
            }
        }
        return uiNode;
    }

    private String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null ? value.asText() : null;
    }
}
