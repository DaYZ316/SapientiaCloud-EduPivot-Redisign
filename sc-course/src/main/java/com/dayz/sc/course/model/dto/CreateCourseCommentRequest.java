package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateCourseCommentRequest(
        @NotBlank String content,
        List<String> imageUrls
) {}
