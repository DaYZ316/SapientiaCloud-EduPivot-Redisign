package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record LivePracticeEventVO(
        @JsonProperty UUID groupId,
        @JsonProperty UUID courseId,
        @JsonProperty UUID classSessionId,
        @JsonProperty String title,
        @JsonProperty Integer totalQuestions,
        @JsonProperty Instant availableStartAt,
        @JsonProperty Instant availableEndAt,
        @JsonProperty Integer allowLateSubmission,
        @JsonProperty Instant publishedAt
) {
}
