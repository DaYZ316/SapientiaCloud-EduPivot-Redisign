package com.dayz.sc.course.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record UpdateChapterRequest(
        @Size(max = 200) String chapterName,
        UUID parentChapterId,
        @Size(max = 2000) String description,
        String content,
        List<@Valid ChapterAttachmentRequest> attachments,
        @Min(0) Integer sortOrder,
        Integer status
) {
}
