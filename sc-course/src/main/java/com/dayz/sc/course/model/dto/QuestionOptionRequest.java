package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * 请求 DTO。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record QuestionOptionRequest(
        @NotBlank String optionContent,
        @NotBlank String optionLabel,
        int isCorrect,
        BigDecimal score,
        List<String> imageUrls,
        String explanation
) {}
