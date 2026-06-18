package com.dayz.sc.course.model.vo;

import com.dayz.sc.course.model.value.LivePracticeAnswerSnapshot;
import com.dayz.sc.course.model.value.LivePracticeOptionSnapshot;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LivePracticeQuestionVO(
        @JsonProperty UUID id,
        @JsonProperty UUID groupId,
        @JsonProperty UUID sourceQuestionId,
        @JsonProperty Integer questionOrder,
        @JsonProperty String questionTitle,
        @JsonProperty String questionContent,
        @JsonProperty Integer questionType,
        @JsonProperty Integer difficulty,
        @JsonProperty BigDecimal score,
        @JsonProperty Integer estimatedTime,
        @JsonProperty List<String> tags,
        @JsonProperty List<String> imageUrls,
        @JsonProperty Integer allowPartialCredit,
        @JsonProperty List<LivePracticeOptionSnapshot> options,
        @JsonProperty List<LivePracticeAnswerSnapshot> answers,
        @JsonProperty Integer aiGradingEnabled,
        @JsonProperty LivePracticeSubmissionVO mySubmission,
        @JsonProperty LivePracticeAnalysisVO analysis,
        @JsonProperty Instant createdAt
) {
}
