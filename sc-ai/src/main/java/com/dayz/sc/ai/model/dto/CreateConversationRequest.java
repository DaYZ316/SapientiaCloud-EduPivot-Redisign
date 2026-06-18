package com.dayz.sc.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建会话请求
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record CreateConversationRequest(
        @NotBlank @Size(max = 255) String title
) {
}
