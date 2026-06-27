package com.dayz.sc.common.dashboard;

import java.time.Instant;
import java.util.UUID;

/**
 * DashboardNotificationItem.
 *
 * @author DaYZ
 */
public record DashboardNotificationItem(
        UUID id,
        Integer type,
        String title,
        Instant createdAt,
        boolean read
) {
}
