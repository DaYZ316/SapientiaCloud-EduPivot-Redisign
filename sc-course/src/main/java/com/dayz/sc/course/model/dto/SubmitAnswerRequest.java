package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record SubmitAnswerRequest(
        @NotNull UUID questionId,
        List<UUID> selectedOptionIds,
        String textAnswer
) {
}
