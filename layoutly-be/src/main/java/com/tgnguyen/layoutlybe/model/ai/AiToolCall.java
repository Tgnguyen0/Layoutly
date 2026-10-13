package com.tgnguyen.layoutlybe.model.ai;

public record AiToolCall(
        String id,
        String type,
        FunctionCall function
) {
    public record FunctionCall(
            String name,
            String arguments
    ) {}
}
