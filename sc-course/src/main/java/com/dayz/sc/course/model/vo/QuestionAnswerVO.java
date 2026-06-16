package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 视图对象。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record QuestionAnswerVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("questionId") UUID questionId,
        @JsonProperty("answerContent") String answerContent,
        @JsonProperty("explanation") @Nullable String explanation,
        @JsonProperty("score") @Nullable BigDecimal score,
        @JsonProperty("sortOrder") int sortOrder
) {}
