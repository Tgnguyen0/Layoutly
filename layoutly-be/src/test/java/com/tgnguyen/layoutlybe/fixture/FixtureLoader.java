package com.tgnguyen.layoutlybe.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FixtureLoader {
    private static final String ROOT = "figma-fixtures/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String loadRawJson(String caseName) {
        return loadText(caseName, "figma-raw.json");
    }

    public JsonNode loadMetadata(String caseName) {
        return loadJson(caseName, "metadata.json");
    }

    public JsonNode loadDesignIr(String caseName) {
        return loadJson(caseName, "design-ir.json");
    }

    public String loadText(String caseName, String fileName) {
        String safeCase = validateSegment(caseName);
        String safeFile = validateSegment(fileName);
        String resource = ROOT + safeCase + "/" + safeFile;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalArgumentException("Fixture resource not found: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new RuntimeException("Cannot read fixture resource: " + resource, exception);
        }
    }

    private JsonNode loadJson(String caseName, String fileName) {
        try {
            return objectMapper.readTree(loadText(caseName, fileName));
        } catch (IOException exception) {
            throw new RuntimeException("Cannot parse fixture JSON", exception);
        }
    }

    private String validateSegment(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid fixture path segment");
        }
        return value;
    }
}
