package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 请求体：更新论坛回复
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record UpdateForumReplyRequest(
        @NotBlank String content,
        List<String> imageUrls
) {
}
