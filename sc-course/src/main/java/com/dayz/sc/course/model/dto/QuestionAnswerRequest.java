package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record QuestionAnswerRequest(
        @NotBlank String answerContent,
        String explanation,
        BigDecimal score,
        int sortOrder
) {}
