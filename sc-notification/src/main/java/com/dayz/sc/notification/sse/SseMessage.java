package com.dayz.sc.notification.sse;

import com.dayz.sc.notification.model.vo.NotificationVO;

import java.util.UUID;

/**
 * SseMessage 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record SseMessage(
        UUID targetUserId,
        UUID excludeUserId,
        NotificationVO notification,
        long unreadCount
) {
    /**
     * 兼容广播场景（count 未知）
     */
    public SseMessage(UUID targetUserId, UUID excludeUserId, NotificationVO notification) {
        this(targetUserId, excludeUserId, notification, -1);
    }

    public boolean isBroadcast() {
        return targetUserId == null;
    }
}
