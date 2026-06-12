package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuestionVO(
        UUID id,
        UUID questionBankId,
        UUID courseId,
        UUID sysUserId,
        String questionTitle,
        @Nullable String questionContent,
        int questionType,
        int difficulty,
        BigDecimal score,
        @Nullable Integer estimatedTime,
        @Nullable List<String> tags,
        @Nullable List<String> imageUrls,
        int allowPartialCredit,
        long viewCount,
        int status,
        @Nullable List<QuestionOptionVO> options,
        @Nullable List<QuestionAnswerVO> answers,
        Instant createdAt,
        Instant updatedAt
) {}
