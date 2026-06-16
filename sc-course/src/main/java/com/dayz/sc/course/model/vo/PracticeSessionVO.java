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
public record PracticeSessionVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("questionBankId") UUID questionBankId,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("sysUserId") UUID sysUserId,
        @JsonProperty("sessionType") int sessionType,
        @JsonProperty("totalQuestions") int totalQuestions,
        @JsonProperty("answeredCount") int answeredCount,
        @JsonProperty("correctCount") int correctCount,
        @JsonProperty("totalScore") BigDecimal totalScore,
        @JsonProperty("earnedScore") BigDecimal earnedScore,
        @JsonProperty("startedAt") Instant startedAt,
        @JsonProperty("completedAt") @Nullable Instant completedAt,
        @JsonProperty("status") int status,
        @JsonProperty("answers") @Nullable List<PracticeAnswerVO> answers
) {}
