package com.dayz.sc.course.model.vo.dashboard;

import com.dayz.sc.common.dashboard.DashboardChartPoint;
import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.dashboard.DashboardUserSummary;

import java.util.List;

public record DashboardAdminVO(
        DashboardUserSummary users,
        DashboardNotificationSummary notifications,
        long courses,
        long publishedCourses,
        List<DashboardChartPoint> courseStatus,
        List<DashboardCapacityItemVO> courseCapacity,
        List<DashboardSummaryItemVO> resources,
        List<DashboardTimelineItemVO> activities,
        List<DashboardRiskItemVO> risks
) {
    public DashboardAdminVO {
        courseStatus = courseStatus == null ? List.of() : List.copyOf(courseStatus);
        courseCapacity = courseCapacity == null ? List.of() : List.copyOf(courseCapacity);
        resources = resources == null ? List.of() : List.copyOf(resources);
        activities = activities == null ? List.of() : List.copyOf(activities);
        risks = risks == null ? List.of() : List.copyOf(risks);
    }
}
