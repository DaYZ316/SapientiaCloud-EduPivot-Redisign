package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 章节附件引用请求
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record ChapterAttachmentRequest(
        @NotNull UUID fileId,
        @Size(max = 255) String displayName
) {
}
