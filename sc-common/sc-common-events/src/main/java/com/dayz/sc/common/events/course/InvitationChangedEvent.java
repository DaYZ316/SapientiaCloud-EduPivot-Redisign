package com.dayz.sc.common.events.course;

import java.time.Instant;
import java.util.UUID;

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
