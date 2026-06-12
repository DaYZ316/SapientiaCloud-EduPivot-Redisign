package com.dayz.sc.common.events.user;

import java.util.UUID;

public record UserRegisteredEvent(
        UUID eventId,
        UUID userId,
        String email,
        String displayName,
        Integer role
) {}
