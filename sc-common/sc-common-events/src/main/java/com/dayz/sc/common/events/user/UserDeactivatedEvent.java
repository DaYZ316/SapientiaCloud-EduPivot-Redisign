package com.dayz.sc.common.events.user;

import java.time.Instant;
import java.util.UUID;

public record UserDeactivatedEvent(
        UUID eventId,
        UUID userId,
        String email,
        String eventType,
        Instant timestamp,
        String source
) {}
