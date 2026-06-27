package com.dayz.sc.common.dashboard;

import java.time.Instant;
import java.util.UUID;

/**
 * DashboardUserActivity.
 *
 * @author DaYZ
 */
public record DashboardUserActivity(
        UUID id,
        String name,
        String email,
        Instant createdAt,
        Instant lastLoginAt
) {
}
