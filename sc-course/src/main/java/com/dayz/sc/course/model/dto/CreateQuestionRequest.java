package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CreateQuestionRequest(
        @NotNull UUID questionBankId,
        @NotBlank @Size(max = 500) String questionTitle,
        String questionContent,
        int questionType,
        int difficulty,
        @Min(0) BigDecimal score,
        Integer estimatedTime,
        List<String> tags,
        List<String> imageUrls,
        int allowPartialCredit,
        List<QuestionOptionRequest> options,
        List<QuestionAnswerRequest> answers
) {}
