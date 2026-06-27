package com.dayz.sc.ai.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * QuestionGenerateOptionRecord.
 *
 * @author DaYZ
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionGenerateOptionRecord(
        @JsonAlias({"OptionContent"}) @Nullable String optionContent,
        @JsonAlias({"OptionLabel"}) @Nullable String optionLabel,
        @JsonAlias({"IsCorrect"}) @Nullable Integer isCorrect,
        @JsonAlias({"Score"}) @Nullable BigDecimal score,
        @JsonAlias({"ImageUrls"}) @Nullable List<String> imageUrls,
        @JsonAlias({"Explanation"}) @Nullable String explanation
) {
}
