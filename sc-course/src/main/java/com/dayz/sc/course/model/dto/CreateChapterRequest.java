package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 请求 DTO。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CreateChapterRequest(
        @NotNull UUID courseId,
        @NotBlank @Size(max = 200) String chapterName,
        UUID parentChapterId,
        @Size(max = 2000) String description,
        String content,
        List<String> attachmentUrls,
        @Min(0) int sortOrder
) {}
