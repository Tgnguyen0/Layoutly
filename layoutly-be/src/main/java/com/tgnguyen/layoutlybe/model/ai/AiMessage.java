package com.tgnguyen.layoutlybe.model.ai;

import java.util.List;

public record AiMessage(
        String role,
        String content,
        String name,
        String toolCallId,
        List<AiToolCall> toolCalls
) {
    public static AiMessage system(String content) {
        return new AiMessage("system", content, null, null, null);
    }

    public static AiMessage user(String content) {
        return new AiMessage("user", content, null, null, null);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage("assistant", content, null, null, null);
    }

    public static AiMessage assistantWithTools(String content, List<AiToolCall> toolCalls) {
        return new AiMessage("assistant", content, null, null, toolCalls);
    }

    public static AiMessage tool(String toolCallId, String name, String content) {
        return new AiMessage("tool", content, name, toolCallId, null);
    }
}
