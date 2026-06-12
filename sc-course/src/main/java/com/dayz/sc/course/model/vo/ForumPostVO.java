package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumPostVO(
        UUID id,
        UUID forumId,
        UUID courseId,
        UUID sysUserId,
        String title,
        String content,
        int postType,
        int isAnonymous,
        @Nullable List<String> attachmentUrls,
        @Nullable List<String> imageUrls,
        @Nullable List<String> tags,
        long viewCount,
        long likeCount,
        long replyCount,
        long shareCount,
        int isTop,
        int isEssence,
        int isLocked,
        @Nullable UUID lastReplyId,
        @Nullable Instant lastReplyTime,
        @Nullable UUID lastReplyUserId,
        int status,
        @Nullable UUID chapterId,
        Instant createdAt,
        Instant updatedAt
) {}
