package com.dayz.sc.common.question;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * QuestionAnswerRequest.
 *
 * @author DaYZ
 */
public record QuestionAnswerRequest(
        @NotBlank String answerContent,
        String explanation,
        BigDecimal score,
        int sortOrder
) {
}
