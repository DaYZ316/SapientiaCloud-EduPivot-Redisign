package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGenerateOptionRecord(
        @JsonAlias({"OptionContent"}) String optionContent,
        @JsonAlias({"OptionLabel"}) String optionLabel,
        @JsonAlias({"IsCorrect"}) Integer isCorrect,
        @JsonAlias({"Score"}) BigDecimal score,
        @JsonAlias({"ImageUrls"}) List<String> imageUrls,
        @JsonAlias({"Explanation"}) String explanation
) {
}
