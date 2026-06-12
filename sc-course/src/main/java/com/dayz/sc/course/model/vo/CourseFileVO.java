package com.dayz.sc.course.model.vo;

import java.time.Instant;
import java.util.UUID;

public record CourseFileVO(
        UUID id,
        UUID courseId,
        UUID fileId,
        String visibility,
        String displayName,
        String url,
        UUID createdBy,
        Integer sortOrder,
        Instant createdAt,
        Instant updatedAt
) {}
