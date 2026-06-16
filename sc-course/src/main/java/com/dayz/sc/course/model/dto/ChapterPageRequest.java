package com.dayz.sc.course.model.dto;

import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record ChapterPageRequest(
        Long page,
        Long size,
        UUID courseId,
        Integer status,
        String keyword
) {}
