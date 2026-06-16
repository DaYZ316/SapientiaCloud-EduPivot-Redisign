package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 视图对象。
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record PracticeAnswerVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("sessionId") UUID sessionId,
        @JsonProperty("questionId") UUID questionId,
        @JsonProperty("selectedOptionIds") @Nullable List<UUID> selectedOptionIds,
        @JsonProperty("textAnswer") @Nullable String textAnswer,
        @JsonProperty("isCorrect") int isCorrect,
        @JsonProperty("earnedScore") BigDecimal earnedScore,
        @JsonProperty("answeredAt") Instant answeredAt
) {}
