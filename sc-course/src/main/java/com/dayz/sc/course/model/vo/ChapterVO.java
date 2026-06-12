package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChapterVO(
        UUID id,
        UUID courseId,
        UUID teacherId,
        String chapterName,
        @Nullable UUID parentChapterId,
        @Nullable String description,
        @Nullable String content,
        @Nullable List<String> attachmentUrls,
        int sortOrder,
        int status,
        long viewCount,
        long likeCount,
        @Nullable List<ChapterVO> children,
        Instant createdAt,
        Instant updatedAt
) {}
