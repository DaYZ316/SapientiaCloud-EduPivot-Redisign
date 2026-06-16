package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 视图对象
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record ChapterVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("teacherId") UUID teacherId,
        @JsonProperty("chapterName") String chapterName,
        @JsonProperty("parentChapterId") @Nullable UUID parentChapterId,
        @JsonProperty("description") @Nullable String description,
        @JsonProperty("content") @Nullable String content,
        @JsonProperty("attachmentUrls") @Nullable List<String> attachmentUrls,
        @JsonProperty("sortOrder") int sortOrder,
        @JsonProperty("status") int status,
        @JsonProperty("viewCount") long viewCount,
        @JsonProperty("likeCount") long likeCount,
        @JsonProperty("likedByMe") boolean likedByMe,
        @JsonProperty("children") @Nullable List<ChapterVO> children,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {
}
