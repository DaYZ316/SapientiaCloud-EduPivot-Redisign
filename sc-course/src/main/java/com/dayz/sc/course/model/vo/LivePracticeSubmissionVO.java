package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        @JsonProperty Instant submittedAt
) {
}
