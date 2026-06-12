package com.dayz.sc.course.model.vo;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumReplyVO(
        UUID id,
        UUID postId,
        UUID forumId,
        UUID courseId,
        UUID sysUserId,
        String content,
        @Nullable UUID parentReplyId,
        @Nullable UUID replyToUserId,
        int isAnonymous,
        @Nullable List<String> attachmentUrls,
        @Nullable List<String> imageUrls,
        long likeCount,
        long replyCount,
        int isAccepted,
        int floorNumber,
        int status,
        @Nullable List<ForumReplyVO> children,
        Instant createdAt,
        Instant updatedAt
) {}
