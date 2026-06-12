package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record QuestionOptionVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("questionId") UUID questionId,
        @JsonProperty("optionContent") String optionContent,
        @JsonProperty("optionLabel") String optionLabel,
        @JsonProperty("isCorrect") int isCorrect,
        @JsonProperty("score") @Nullable BigDecimal score,
        @JsonProperty("imageUrls") @Nullable List<String> imageUrls,
        @JsonProperty("explanation") @Nullable String explanation
) {}
