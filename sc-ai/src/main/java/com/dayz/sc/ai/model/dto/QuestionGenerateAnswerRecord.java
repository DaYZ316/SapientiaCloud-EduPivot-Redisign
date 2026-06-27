package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * QuestionGenerateAnswerRecord.
 *
 * @author DaYZ
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGenerateAnswerRecord(
        @JsonAlias({"AnswerContent"}) @Nullable String answerContent,
        @JsonAlias({"Explanation"}) @Nullable String explanation,
        @JsonAlias({"Score"}) @Nullable BigDecimal score,
        @JsonAlias({"SortOrder"}) @Nullable Integer sortOrder
) {
}
