package com.tgnguyen.layoutlybe.model.ai;

import java.util.List;

public record AiChatRequest(
        String prompt,
        String targetFramework, // "REACT" or "HTML"
        String selectedNodeId,
        String selectedNodeName,
        List<AiMessage> history,
        String provider,
        String model
) {
    public AiChatRequest(String prompt, String targetFramework, String selectedNodeId, String selectedNodeName, List<AiMessage> history) {
        this(prompt, targetFramework, selectedNodeId, selectedNodeName, history, null, null);
    }
}
