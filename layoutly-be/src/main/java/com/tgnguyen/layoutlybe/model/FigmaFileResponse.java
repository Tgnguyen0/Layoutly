package com.tgnguyen.layoutlybe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response cua GET /v1/files/:key
 * "document" chinh la node goc, luon co type = "DOCUMENT",
 * children cua no la cac CANVAS (moi Page trong Figma la 1 CANVAS).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FigmaFileResponse(
        String name,
        FigmaNode document
) {}
