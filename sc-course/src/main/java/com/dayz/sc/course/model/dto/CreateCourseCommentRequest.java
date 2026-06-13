package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseCommentRequest(
        @NotBlank String content,
        Integer isAnonymous
) {}
