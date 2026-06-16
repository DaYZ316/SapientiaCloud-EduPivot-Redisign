package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record CreateCourseCommentRequest(
        @NotBlank String content,
        List<String> imageUrls
) {}
