package com.dayz.sc.common.events.course;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CourseDeletedEvent(
        UUID eventId,
        UUID courseId,
        String title,
        UUID teacherId,
        String eventType,
        Instant timestamp,
        String source
) {
}
