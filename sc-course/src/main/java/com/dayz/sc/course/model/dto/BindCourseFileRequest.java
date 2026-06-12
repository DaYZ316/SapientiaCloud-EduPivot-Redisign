package com.dayz.sc.course.model.dto;

import com.dayz.sc.course.model.enums.CourseFileVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BindCourseFileRequest(
        @NotNull UUID fileId,
        @NotNull CourseFileVisibility visibility,
        @Size(max = 255) String displayName,
        Integer sortOrder
) {}
