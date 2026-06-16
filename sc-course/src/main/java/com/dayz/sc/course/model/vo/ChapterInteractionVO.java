package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * 视图对象
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public record ChapterInteractionVO(
        @JsonProperty("chapterId") UUID chapterId,
        @JsonProperty("viewCount") long viewCount,
        @JsonProperty("likeCount") long likeCount,
        @JsonProperty("likedByMe") boolean likedByMe
) {}
