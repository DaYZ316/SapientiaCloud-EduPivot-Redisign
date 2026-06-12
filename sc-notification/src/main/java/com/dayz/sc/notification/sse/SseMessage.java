package com.dayz.sc.notification.sse;

import com.dayz.sc.notification.model.vo.NotificationVO;

import java.util.UUID;

public record SseMessage(
        UUID targetUserId,
        UUID excludeUserId,
        NotificationVO notification
) {
    public boolean isBroadcast() {
        return targetUserId == null;
    }
}
