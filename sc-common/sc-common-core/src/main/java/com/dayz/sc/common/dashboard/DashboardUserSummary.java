package com.dayz.sc.common.dashboard;

import java.util.List;

public record DashboardUserSummary(
        long totalUsers,
        long students,
        long teachers,
        long admins,
        long disabledUsers,
        long todayUsers,
        long incompleteProfiles,
        int oauthPercent,
        List<DashboardChartPoint> roleDistribution,
        List<DashboardUserActivity> recentUsers
) {
    public DashboardUserSummary {
        roleDistribution = roleDistribution == null ? List.of() : List.copyOf(roleDistribution);
        recentUsers = recentUsers == null ? List.of() : List.copyOf(recentUsers);
    }

    public static DashboardUserSummary empty() {
        return new DashboardUserSummary(0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
    }
}
