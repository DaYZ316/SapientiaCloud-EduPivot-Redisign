package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateCourseRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @Min(1) Integer level,
        String coverUrl,
        UUID coverFileId,
        UUID teacherId,
        List<UUID> assistantIds,
        @Size(max = 20) String semester,
        @Size(max = 100) String location,
        Integer courseType,
        @NotNull @Min(0) @Max(1) Integer isPublic,
        @Min(0) Integer maxStudents,
        Integer status
) {}
