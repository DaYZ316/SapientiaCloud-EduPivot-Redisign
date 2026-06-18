package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 随堂练习提交记录视图对象
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record LivePracticeSubmissionVO(
        @JsonProperty UUID id,
        @JsonProperty UUID groupId,
        @JsonProperty UUID questionSnapshotId,
        @JsonProperty UUID courseId,
        @JsonProperty UUID classSessionId,
        @JsonProperty UUID studentId,
        @JsonProperty String studentName,
        @JsonProperty String studentAvatarUrl,
        @JsonProperty List<UUID> selectedOptionIds,
        @JsonProperty String textAnswer,
        @JsonProperty Integer submitStatus,
        @JsonProperty String submitStatusText,
        @JsonProperty Integer isCorrect,
        @JsonProperty BigDecimal earnedScore,
        @JsonProperty String aiGradingStatus,
        @JsonProperty String aiGradingFeedback,
        @JsonProperty String aiGradingError,
        @JsonProperty Instant aiGradedAt,
        @JsonProperty Instant submittedAt
) {
}
