package com.dayz.sc.course.model.vo.dashboard;

import java.util.UUID;

public record DashboardQuestionCoverageVO(
        UUID courseId,
        String courseTitle,
        long bankCount,
        long questionCount
) {
}
