package com.dayz.sc.course.model.vo.dashboard;

import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.course.model.vo.EnrollmentVO;
import com.dayz.sc.course.model.vo.PracticeSessionVO;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record DashboardStudentVO(
        DashboardNotificationSummary notifications,
        List<EnrollmentVO> enrollments,
        List<ClassSessionVO> sessions,
        List<CourseVO> recommendations,
        List<ClassSessionVO> ongoingSessions,
        @Nullable ClassSessionVO liveSession,
        @Nullable EnrollmentVO continueCourse,
        DashboardActionVO practiceFocus,
        List<PracticeSessionVO> practiceSessions,
        List<DashboardTimelineItemVO> todos
) {
    public DashboardStudentVO {
        enrollments = enrollments == null ? List.of() : List.copyOf(enrollments);
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        ongoingSessions = ongoingSessions == null ? List.of() : List.copyOf(ongoingSessions);
        practiceSessions = practiceSessions == null ? List.of() : List.copyOf(practiceSessions);
        todos = todos == null ? List.of() : List.copyOf(todos);
    }
}
