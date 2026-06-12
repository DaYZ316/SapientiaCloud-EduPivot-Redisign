package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("forumName") String forumName,
        @JsonProperty("description") @Nullable String description,
        @JsonProperty("forumType") int forumType,
        @JsonProperty("allowAnonymous") int allowAnonymous,
        @JsonProperty("postCount") long postCount,
        @JsonProperty("replyCount") long replyCount,
        @JsonProperty("status") int status,
        @JsonProperty("tags") @Nullable List<String> tags,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {}
