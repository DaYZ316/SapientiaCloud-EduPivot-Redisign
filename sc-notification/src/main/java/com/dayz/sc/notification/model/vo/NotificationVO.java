package com.dayz.sc.notification.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * 通知视图对象
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public record NotificationVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("type") Integer type,
        @JsonProperty("title") String title,
        @JsonProperty("content") String content,
        @JsonProperty("senderId") UUID senderId,
        @JsonProperty("targetType") Integer targetType,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt,
        @JsonProperty("isRead") Boolean read,
        @JsonProperty("readAt") Instant readAt
) {
}
