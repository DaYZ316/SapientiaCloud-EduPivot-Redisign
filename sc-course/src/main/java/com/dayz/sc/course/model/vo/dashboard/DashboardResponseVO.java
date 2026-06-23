package com.dayz.sc.course.model.vo.dashboard;

import org.jspecify.annotations.Nullable;

public record DashboardResponseVO(
        int role,
        @Nullable DashboardAdminVO admin,
        @Nullable DashboardStudentVO student,
        @Nullable DashboardTeacherVO teacher
) {
}
