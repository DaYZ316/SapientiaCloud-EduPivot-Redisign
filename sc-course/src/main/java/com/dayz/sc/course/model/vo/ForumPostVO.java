package com.dayz.sc.course.model.vo;

import com.dayz.sc.common.feign.dto.UserBasicInfo;
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
public record ForumPostVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("sysUserId") UUID sysUserId,
        @JsonProperty("userInfo") @Nullable UserBasicInfo userInfo,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("postType") int postType,
        @JsonProperty("attachmentUrls") @Nullable List<String> attachmentUrls,
        @JsonProperty("imageUrls") @Nullable List<String> imageUrls,
        @JsonProperty("tags") @Nullable List<String> tags,
        @JsonProperty("viewCount") long viewCount,
        @JsonProperty("likeCount") long likeCount,
        @JsonProperty("replyCount") long replyCount,
        @JsonProperty("shareCount") long shareCount,
        @JsonProperty("isTop") int isTop,
        @JsonProperty("isEssence") int isEssence,
        @JsonProperty("isLocked") int isLocked,
        @JsonProperty("lastReplyId") @Nullable UUID lastReplyId,
        @JsonProperty("lastReplyTime") @Nullable Instant lastReplyTime,
        @JsonProperty("lastReplyUserId") @Nullable UUID lastReplyUserId,
        @JsonProperty("status") int status,
        @JsonProperty("chapterId") @Nullable UUID chapterId,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {
}
