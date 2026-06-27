package com.dayz.sc.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 问答请求
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record ChatRequest(
        @Nullable UUID conversationId,
        @NotBlank @Size(max = 4000) String message,
        @Nullable String agentMode,
        @Nullable UUID courseId,
        @Nullable GenerationRequest generation
) {
}
