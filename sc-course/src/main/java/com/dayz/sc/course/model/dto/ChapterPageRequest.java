package com.dayz.sc.course.model.dto;

import java.util.UUID;

public record ChapterPageRequest(
        Long page,
        Long size,
        UUID courseId,
        Integer status,
        String keyword
) {}
