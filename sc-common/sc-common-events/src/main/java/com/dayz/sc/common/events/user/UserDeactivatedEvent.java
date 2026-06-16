package com.dayz.sc.common.events.user;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件定义。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record UserDeactivatedEvent(
        UUID eventId,
        UUID userId,
        String email,
        String eventType,
        Instant timestamp,
        String source
) {}
