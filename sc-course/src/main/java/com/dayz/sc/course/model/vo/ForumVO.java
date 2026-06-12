package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumVO(
        UUID id,
        UUID courseId,
        String forumName,
        @Nullable String description,
        int forumType,
        int allowAnonymous,
        long postCount,
        long replyCount,
        int status,
        @Nullable List<String> tags,
        Instant createdAt,
        Instant updatedAt
) {}
