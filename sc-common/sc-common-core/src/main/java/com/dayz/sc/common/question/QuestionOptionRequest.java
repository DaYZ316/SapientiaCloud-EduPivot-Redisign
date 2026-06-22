package com.dayz.sc.common.question;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

public record QuestionOptionRequest(
        @NotBlank String optionContent,
        @NotBlank String optionLabel,
        int isCorrect,
        BigDecimal score,
        List<String> imageUrls,
        String explanation
) {
}
