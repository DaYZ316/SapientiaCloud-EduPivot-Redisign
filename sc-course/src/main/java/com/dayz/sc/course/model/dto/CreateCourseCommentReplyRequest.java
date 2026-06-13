package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateCourseCommentReplyRequest(
        @NotBlank String content,
        UUID parentReplyId,
        UUID replyToUserId,
        Integer isAnonymous
) {}
