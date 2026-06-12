package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateEnrollmentStatusRequest(
        @NotNull Integer status
) {
}
