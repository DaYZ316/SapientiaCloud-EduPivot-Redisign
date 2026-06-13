package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePracticeSessionRequest(
        @NotNull UUID questionBankId,
        @NotNull UUID courseId,
        int sessionType
) {}
