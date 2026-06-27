package com.dayz.sc.course.model.vo.dashboard;

import org.jspecify.annotations.Nullable;

/**
 * DashboardResponseVO.
 *
 * @author DaYZ
 */
public record DashboardResponseVO(
        int role,
        @Nullable DashboardAdminVO admin,
        @Nullable DashboardStudentVO student,
        @Nullable DashboardTeacherVO teacher
) {
}
