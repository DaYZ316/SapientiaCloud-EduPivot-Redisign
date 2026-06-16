package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record UpdateQuestionRequest(
        @Size(max = 500) String questionTitle,
        String questionContent,
        Integer questionType,
        Integer difficulty,
        @Min(0) BigDecimal score,
        Integer estimatedTime,
        List<String> tags,
        List<String> imageUrls,
        Integer allowPartialCredit,
        List<QuestionOptionRequest> options,
        List<QuestionAnswerRequest> answers
) {}
