package com.dayz.sc.common.events.course;

import java.time.Instant;
import java.util.UUID;

public record CourseDeletedEvent(
        UUID eventId,
        UUID courseId,
        String title,
        UUID teacherId,
        String eventType,
        Instant timestamp,
        String source
) {}
