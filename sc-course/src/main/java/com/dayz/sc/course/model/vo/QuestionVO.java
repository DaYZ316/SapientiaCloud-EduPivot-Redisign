package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 视图对象
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record QuestionVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("questionBankId") UUID questionBankId,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("sysUserId") UUID sysUserId,
        @JsonProperty("questionTitle") String questionTitle,
        @JsonProperty("questionContent") @Nullable String questionContent,
        @JsonProperty("questionType") int questionType,
        @JsonProperty("difficulty") int difficulty,
        @JsonProperty("score") BigDecimal score,
        @JsonProperty("estimatedTime") @Nullable Integer estimatedTime,
        @JsonProperty("tags") @Nullable List<String> tags,
        @JsonProperty("imageUrls") @Nullable List<String> imageUrls,
        @JsonProperty("allowPartialCredit") int allowPartialCredit,
        @JsonProperty("viewCount") long viewCount,
        @JsonProperty("status") int status,
        @JsonProperty("options") @Nullable List<QuestionOptionVO> options,
        @JsonProperty("answers") @Nullable List<QuestionAnswerVO> answers,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {}
