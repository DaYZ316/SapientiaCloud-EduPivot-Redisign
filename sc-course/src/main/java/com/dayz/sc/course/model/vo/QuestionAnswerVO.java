package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

public record QuestionAnswerVO(
        UUID id,
        UUID questionId,
        String answerContent,
        @Nullable String explanation,
        @Nullable BigDecimal score,
        int sortOrder
) {}
