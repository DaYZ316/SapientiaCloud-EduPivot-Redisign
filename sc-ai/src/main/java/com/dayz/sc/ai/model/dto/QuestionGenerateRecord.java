package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * QuestionGenerateRecord.
 *
 * @author DaYZ
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGenerateRecord(
        @Nullable String questionTitle,
        @Nullable String questionContent,
        @Nullable Integer questionType,
        @Nullable Integer difficulty,
        @Nullable BigDecimal score,
        @Nullable Integer estimatedTime,
        @Nullable List<String> tags,
        @Nullable List<QuestionGenerateOptionRecord> options,
        @Nullable List<QuestionGenerateAnswerRecord> answers
) {
}
