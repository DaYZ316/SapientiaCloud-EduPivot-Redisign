package com.dayz.sc.notification.sse;

import com.dayz.sc.notification.model.vo.NotificationVO;

import java.util.UUID;

public record SseMessage(
        UUID targetUserId,
        UUID excludeUserId,
        NotificationVO notification,
        long unreadCount
) {
    public boolean isBroadcast() {
        return targetUserId == null;
    }

    /** 兼容广播场景（count 未知） */
    public SseMessage(UUID targetUserId, UUID excludeUserId, NotificationVO notification) {
        this(targetUserId, excludeUserId, notification, -1);
    }
}
