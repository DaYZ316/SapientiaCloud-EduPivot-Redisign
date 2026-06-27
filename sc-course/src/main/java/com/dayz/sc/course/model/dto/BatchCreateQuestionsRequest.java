package com.dayz.sc.course.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * BatchCreateQuestionsRequest.
 *
 * @author DaYZ
 */
public record BatchCreateQuestionsRequest(
        @NotNull UUID questionBankId,
        @NotEmpty @Size(max = 50) List<@Valid QuestionImportRequest> questions
) {
}
