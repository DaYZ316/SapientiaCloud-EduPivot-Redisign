package com.dayz.sc.common.events.course;

import java.time.Instant;
import java.util.UUID;

public record CourseStatusChangedEvent(
        UUID eventId,
        UUID courseId,
        String courseTitle,
        UUID teacherId,
        String action,
        String eventType,
        Instant timestamp,
        String source
) {
}
