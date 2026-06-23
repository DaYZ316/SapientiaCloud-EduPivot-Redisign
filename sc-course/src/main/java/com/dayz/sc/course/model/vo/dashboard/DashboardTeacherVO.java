package com.dayz.sc.course.model.vo.dashboard;

import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.CourseVO;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record DashboardTeacherVO(
        DashboardNotificationSummary notifications,
        List<CourseVO> primaryCourses,
        List<CourseVO> assistantCourses,
        List<CourseVO> courses,
        List<ClassSessionVO> sessions,
        @Nullable ClassSessionVO liveSession,
        List<DashboardTimelineItemVO> pending,
        List<DashboardSummaryItemVO> insights,
        List<DashboardQuestionCoverageVO> questionCoverage,
        List<DashboardCapacityItemVO> capacityRanking
) {
    public DashboardTeacherVO {
        primaryCourses = primaryCourses == null ? List.of() : List.copyOf(primaryCourses);
        assistantCourses = assistantCourses == null ? List.of() : List.copyOf(assistantCourses);
        courses = courses == null ? List.of() : List.copyOf(courses);
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        pending = pending == null ? List.of() : List.copyOf(pending);
        insights = insights == null ? List.of() : List.copyOf(insights);
        questionCoverage = questionCoverage == null ? List.of() : List.copyOf(questionCoverage);
        capacityRanking = capacityRanking == null ? List.of() : List.copyOf(capacityRanking);
    }
}
