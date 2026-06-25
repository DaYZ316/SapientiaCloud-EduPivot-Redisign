package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGenerateRecord(
        String questionTitle,
        String questionContent,
        Integer questionType,
        Integer difficulty,
        BigDecimal score,
        Integer estimatedTime,
        List<String> tags,
        List<QuestionGenerateOptionRecord> options,
        List<QuestionGenerateAnswerRecord> answers
) {
}
