package com.dayz.sc.common.events.course;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件定义。
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record InvitationChangedEvent(
        UUID eventId,
        UUID courseId,
        String courseTitle,
        UUID inviterId,
        String inviterName,
        UUID inviteeId,
        String inviteeName,
        String action,
        String eventType,
        Instant timestamp,
        String source
) {
}
