package com.dayz.sc.notification.model.vo;

import java.time.Instant;
import java.util.UUID;

/**
 * 通知视图对象。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public record NotificationVO(
        UUID id,
        Integer type,
        String title,
        String content,
        UUID senderId,
        Integer targetType,
        Instant createdAt,
        Instant updatedAt,
        Boolean isRead,
        Instant readAt
) {
}
