package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 对话消息视图对象
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record ChatMessageVO(
        UUID id,
        String role,
        String content,
        String messageType,
        Map<String, Object> payload,
        Instant createdAt
) {
}
