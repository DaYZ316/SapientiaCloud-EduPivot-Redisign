package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SubmitAnswerRequest(
        @NotNull UUID questionId,
        List<UUID> selectedOptionIds,
        String textAnswer
) {}
