package com.dayz.sc.course.model.dto;

import java.time.Instant;

/**
 * 请求 DTO。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CoursePageRequest(
        Long page,
        Long size,
        String keyword,
        Integer level,
        Integer status,
        Integer isPublic,
        Instant createdAtStart,
        Instant createdAtEnd,
        Instant updatedAtStart,
        Instant updatedAtEnd
) {}
