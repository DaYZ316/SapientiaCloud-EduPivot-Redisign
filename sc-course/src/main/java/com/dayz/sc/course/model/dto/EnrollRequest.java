package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EnrollRequest(
        @NotNull UUID courseId
) {}
