package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.UUID;

/**
 * 会话视图对象。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record ConversationVO(
        UUID id,
        String title,
        boolean pinned,
        boolean favorited,
        Instant createdAt,
        Instant updatedAt
) {
}
