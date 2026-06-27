package com.dayz.sc.common.dashboard;

import java.util.List;

/**
 * DashboardNotificationSummary.
 *
 * @author DaYZ
 */
public record DashboardNotificationSummary(
        long unreadTotal,
        long unreadSystem,
        long unreadTeaching,
        List<DashboardNotificationItem> recent,
        List<DashboardChartPoint> distribution
) {
    public DashboardNotificationSummary {
        recent = recent == null ? List.of() : List.copyOf(recent);
        distribution = distribution == null ? List.of() : List.copyOf(distribution);
    }

    public static DashboardNotificationSummary empty() {
        return new DashboardNotificationSummary(0, 0, 0, List.of(), List.of());
    }
}
