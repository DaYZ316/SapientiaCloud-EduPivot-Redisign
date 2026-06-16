package com.dayz.sc.common.events.user;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件定义。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String displayName,
        Integer role,
        String eventType,
        Instant timestamp,
        String source
) {}
