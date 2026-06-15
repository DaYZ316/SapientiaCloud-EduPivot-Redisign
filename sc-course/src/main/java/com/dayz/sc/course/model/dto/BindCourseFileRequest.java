package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BindCourseFileRequest(
        @NotNull UUID fileId,
        @Size(max = 255) String displayName,
        Integer sortOrder
) {}
