package com.dayz.sc.ai.model.vo;

import com.dayz.sc.ai.model.enums.AiMessageType;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * AiAgentResult.
 *
 * @author DaYZ
 */
public record AiAgentResult(
        String content,
        AiMessageType messageType,
        @Nullable Map<String, Object> payload
) {
}
