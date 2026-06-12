package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuestionOptionVO(
        UUID id,
        UUID questionId,
        String optionContent,
        String optionLabel,
        int isCorrect,
        @Nullable BigDecimal score,
        @Nullable List<String> imageUrls,
        @Nullable String explanation
) {}
