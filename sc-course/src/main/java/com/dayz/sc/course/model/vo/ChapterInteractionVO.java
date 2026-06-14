package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ChapterInteractionVO(
        @JsonProperty("chapterId") UUID chapterId,
        @JsonProperty("viewCount") long viewCount,
        @JsonProperty("likeCount") long likeCount,
        @JsonProperty("likedByMe") boolean likedByMe
) {}
