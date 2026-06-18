package com.dayz.sc.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 问答请求
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record ChatRequest(
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 4000) String message,
        String agentMode,
        UUID courseId,
        GenerationRequest generation
) {
}
