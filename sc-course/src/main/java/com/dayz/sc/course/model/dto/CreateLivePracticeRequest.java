package com.dayz.sc.course.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateLivePracticeRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant availableStartAt,
        @NotNull Instant availableEndAt,
        Integer allowLateSubmission,
        List<UUID> selectedQuestionIds,
        @Valid List<CreateLivePracticeQuestionRequest> createdQuestions
) {
}
