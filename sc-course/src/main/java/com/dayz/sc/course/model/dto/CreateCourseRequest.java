package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CreateCourseRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @Min(1) int level,
        String coverUrl,
        UUID coverFileId,
        List<UUID> assistantIds,
        @Size(max = 20) String semester,
        @Size(max = 100) String location,
        Integer courseType,
        @NotNull @Min(0) @Max(1) Integer isPublic,
        @Min(0) int maxStudents,
        @Min(0) Integer totalClassHours
) {
}
