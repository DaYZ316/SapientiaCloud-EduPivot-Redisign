package com.dayz.sc.common.events.course;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentChangedEvent(
        UUID eventId,
        UUID courseId,
        String courseTitle,
        UUID studentId,
        String studentName,
        UUID teacherId,
        String action,
        String eventType,
        Instant timestamp,
        String source
) {
}
