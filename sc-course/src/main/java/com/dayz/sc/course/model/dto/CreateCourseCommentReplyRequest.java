package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record CreateCourseCommentReplyRequest(
        @NotBlank String content,
        UUID parentReplyId,
        UUID replyToUserId,
        List<String> imageUrls
) {
}
