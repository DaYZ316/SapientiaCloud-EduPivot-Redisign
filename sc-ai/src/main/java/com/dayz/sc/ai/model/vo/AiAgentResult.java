package com.dayz.sc.ai.model.vo;

import com.dayz.sc.ai.model.enums.AiMessageType;

import java.util.Map;

public record AiAgentResult(
        String content,
        AiMessageType messageType,
        Map<String, Object> payload
) {
}
