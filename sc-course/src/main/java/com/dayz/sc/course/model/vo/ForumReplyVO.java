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
public record ForumReplyVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("postId") UUID postId,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("sysUserId") UUID sysUserId,
        @JsonProperty("content") String content,
        @JsonProperty("parentReplyId") @Nullable UUID parentReplyId,
        @JsonProperty("replyToUserId") @Nullable UUID replyToUserId,
        @JsonProperty("attachmentUrls") @Nullable List<String> attachmentUrls,
        @JsonProperty("imageUrls") @Nullable List<String> imageUrls,
        @JsonProperty("likeCount") long likeCount,
        @JsonProperty("replyCount") long replyCount,
        @JsonProperty("isAccepted") int isAccepted,
        @JsonProperty("floorNumber") int floorNumber,
        @JsonProperty("status") int status,
        @JsonProperty("children") @Nullable List<ForumReplyVO> children,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {}
