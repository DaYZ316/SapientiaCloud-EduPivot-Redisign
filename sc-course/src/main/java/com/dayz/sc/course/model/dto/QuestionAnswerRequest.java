package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record QuestionAnswerRequest(
        @NotBlank String answerContent,
        String explanation,
        BigDecimal score,
        int sortOrder
) {
}
