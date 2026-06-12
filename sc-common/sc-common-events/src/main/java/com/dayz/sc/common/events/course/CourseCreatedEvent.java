package com.dayz.sc.common.events.course;

import java.time.Instant;
import java.util.UUID;

public record CourseCreatedEvent(
        UUID eventId,
        UUID courseId,
        String title,
        UUID teacherId,
        String semester,
        String eventType,
        Instant timestamp,
        String source
) {}
