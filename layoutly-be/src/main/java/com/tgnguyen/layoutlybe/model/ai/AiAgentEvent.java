package com.tgnguyen.layoutlybe.model.ai;

import java.time.Instant;

public record AiAgentEvent(String phase, String tool, String detail, Instant at) {
    public static AiAgentEvent of(String phase, String tool, String detail) {
        return new AiAgentEvent(phase, tool, detail, Instant.now());
    }
}
