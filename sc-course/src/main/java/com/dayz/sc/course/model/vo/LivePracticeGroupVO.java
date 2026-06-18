package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LivePracticeGroupVO(
        @JsonProperty UUID id,
        @JsonProperty UUID courseId,
        @JsonProperty UUID classSessionId,
        @JsonProperty UUID teacherId,
        @JsonProperty String title,
        @JsonProperty Instant availableStartAt,
        @JsonProperty Instant availableEndAt,
        @JsonProperty Integer allowLateSubmission,
        @JsonProperty Integer publishOrder,
        @JsonProperty Instant publishedAt,
        @JsonProperty Integer totalQuestions,
        @JsonProperty Integer submittedStudents,
        @JsonProperty Integer totalStudents,
        @JsonProperty List<LivePracticeQuestionVO> questions
) {
}
