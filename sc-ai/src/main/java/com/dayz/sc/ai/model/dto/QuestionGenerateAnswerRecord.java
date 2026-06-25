package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGenerateAnswerRecord(
        @JsonAlias({"AnswerContent"}) String answerContent,
        @JsonAlias({"Explanation"}) String explanation,
        @JsonAlias({"Score"}) BigDecimal score,
        @JsonAlias({"SortOrder"}) Integer sortOrder
) {
}
