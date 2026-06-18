package com.dayz.sc.ai.model.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * 更新会话请求（标题/置顶/收藏，均可选）
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record UpdateConversationRequest(
        @Nullable @Size(max = 255) String title,
        @Nullable Boolean pinned,
        @Nullable Boolean favorited
) {
}
