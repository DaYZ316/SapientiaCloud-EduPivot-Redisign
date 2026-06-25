package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGeneratePayload(
        List<QuestionGenerateRecord> questions
) {

    public List<QuestionGenerateRecord> questionsOrEmpty() {
        return questions != null ? questions : List.of();
    }
}
