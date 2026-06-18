package com.dayz.sc.course.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建随堂练习题目请求
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record CreateLivePracticeQuestionRequest(
        @NotBlank @Size(max = 500) String questionTitle,
        String questionContent,
        int questionType,
        int difficulty,
        @Min(0) BigDecimal score,
        Integer estimatedTime,
        List<String> tags,
        List<String> imageUrls,
        int allowPartialCredit,
        int aiGradingEnabled,
        @Valid List<QuestionOptionRequest> options,
        @Valid List<QuestionAnswerRequest> answers
) {
}
