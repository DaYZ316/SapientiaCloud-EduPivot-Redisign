package com.dayz.sc.common.events.user;

import java.util.UUID;

public record UserDeactivatedEvent(
        UUID eventId,
        UUID userId,
        String email
) {}
