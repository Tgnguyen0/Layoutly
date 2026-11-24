package com.tgnguyen.layoutlybe.model.ai;

public record AiUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        double estimatedCostUsd
) {
    public static AiUsage empty() {
        return new AiUsage(0, 0, 0, 0.0);
    }
}
