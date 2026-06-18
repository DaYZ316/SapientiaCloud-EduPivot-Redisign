package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record LivePracticeWorkbookItemVO(
        @JsonProperty UUID groupId,
        @JsonProperty String groupTitle,
        @JsonProperty UUID classSessionId,
        @JsonProperty Integer publishOrder,
        @JsonProperty Instant availableStartAt,
        @JsonProperty Instant availableEndAt,
        @JsonProperty Integer allowLateSubmission,
        @JsonProperty LivePracticeQuestionVO question,
        @JsonProperty LivePracticeSubmissionVO submission,
        @JsonProperty Integer submitStatus,
        @JsonProperty String submitStatusText
) {
}
