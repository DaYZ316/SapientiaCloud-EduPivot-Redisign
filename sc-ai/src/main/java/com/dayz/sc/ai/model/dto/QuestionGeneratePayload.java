package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * QuestionGeneratePayload.
 *
 * @author DaYZ
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGeneratePayload(
        @Nullable List<QuestionGenerateRecord> questions
) {

    public List<QuestionGenerateRecord> questionsOrEmpty() {
        return questions != null ? questions : List.of();
    }
}
