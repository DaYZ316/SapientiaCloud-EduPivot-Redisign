package com.dayz.sc.course.service;

import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthDashboardInternalClient;
import com.dayz.sc.common.feign.client.NotificationDashboardInternalClient;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.course.model.dto.CoursePageRequest;
import com.dayz.sc.course.model.enums.ClassLiveStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.course.model.vo.EnrollmentVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardResponseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private CourseService courseService;

    @Mock
    private EnrollmentService enrollmentService;

    @Mock
    private ClassSessionService classSessionService;

    @Mock
    private QuestionBankService questionBankService;

    @Mock
    private PracticeSessionService practiceSessionService;

    @Mock
    private AuthDashboardInternalClient authDashboardInternalClient;

    @Mock
    private NotificationDashboardInternalClient notificationDashboardInternalClient;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                courseService,
                enrollmentService,
                classSessionService,
                questionBankService,
                practiceSessionService,
                authDashboardInternalClient,
                notificationDashboardInternalClient
        );
    }

    @Test
    void getDashboard_shouldReturnAdminDashboard_whenInternalSummariesFallback() {
        UUID adminId = UUID.randomUUID();
        when(authDashboardInternalClient.getUserSummary()).thenReturn(ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE));
        when(notificationDashboardInternalClient.getSummary(adminId, true))
                .thenReturn(ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE));
        when(courseService.listCourses(any(CoursePageRequest.class))).thenReturn(PageResponse.empty(1, 8));

        DashboardResponseVO response = dashboardService.getDashboard(adminId, 0);

        assertThat(response.role()).isEqualTo(0);
        assertThat(response.admin()).isNotNull();
        assertThat(response.admin().users().totalUsers()).isZero();
        assertThat(response.admin().notifications().unreadTotal()).isZero();
    }

    @Test
    void getDashboard_shouldOnlyLoadStudentSessionsForAccessibleEnrollments() {
        UUID studentId = UUID.randomUUID();
        UUID pendingCourseId = UUID.randomUUID();
        UUID activeCourseId = UUID.randomUUID();
        UUID droppedCourseId = UUID.randomUUID();
        EnrollmentVO pending = enrollment(pendingCourseId, EnrollmentStatus.PENDING.getCode(), 10);
        EnrollmentVO active = enrollment(activeCourseId, EnrollmentStatus.ACTIVE.getCode(), 50);
        EnrollmentVO dropped = enrollment(droppedCourseId, EnrollmentStatus.DROPPED.getCode(), 20);
        ClassSessionVO session = session(activeCourseId);

        when(notificationDashboardInternalClient.getSummary(studentId, false))
                .thenReturn(ApiResponse.ok(DashboardNotificationSummary.empty()));
        when(enrollmentService.listStudentEnrollments(studentId, 1, 8))
                .thenReturn(PageResponse.of(List.of(pending, active, dropped), 3, 1, 8));
        when(classSessionService.listByCourse(activeCourseId, 1, 4, studentId, 1))
                .thenReturn(PageResponse.of(List.of(session), 1, 1, 4));
        when(courseService.listCourses(any(CoursePageRequest.class))).thenReturn(PageResponse.empty(1, 4));
        when(practiceSessionService.getMyPracticeHistory(studentId)).thenReturn(List.of());

        DashboardResponseVO response = dashboardService.getDashboard(studentId, 1);

        assertThat(response.student()).isNotNull();
        assertThat(response.student().sessions()).containsExactly(session);
        verify(classSessionService, never()).listByCourse(eq(pendingCourseId), eq(1), eq(4), eq(studentId), eq(1));
        verify(classSessionService, never()).listByCourse(eq(droppedCourseId), eq(1), eq(4), eq(studentId), eq(1));
    }

    @Test
    void getDashboard_shouldUseOnlyPrimaryTeacherCoursesForSessionsAndCoverage() {
        UUID teacherId = UUID.randomUUID();
        CourseVO primaryCourse = course(UUID.randomUUID(), teacherId, "Primary");
        CourseVO assistantCourse = course(UUID.randomUUID(), UUID.randomUUID(), "Assistant");

        when(notificationDashboardInternalClient.getSummary(teacherId, false))
                .thenReturn(ApiResponse.ok(DashboardNotificationSummary.empty()));
        when(courseService.listTeacherCourses(teacherId, "primary", 1, 8))
                .thenReturn(PageResponse.of(List.of(primaryCourse), 1, 1, 8));
        when(courseService.listTeacherCourses(teacherId, "assistant", 1, 6))
                .thenReturn(PageResponse.of(List.of(assistantCourse), 1, 1, 6));
        when(classSessionService.listByCourse(primaryCourse.id(), 1, 4, teacherId, 2))
                .thenReturn(PageResponse.empty(1, 4));
        when(questionBankService.listQuestionBanksByCourse(primaryCourse.id())).thenReturn(List.of());

        DashboardResponseVO response = dashboardService.getDashboard(teacherId, 2);

        assertThat(response.teacher()).isNotNull();
        assertThat(response.teacher().primaryCourses()).containsExactly(primaryCourse);
        assertThat(response.teacher().assistantCourses()).containsExactly(assistantCourse);
        verify(classSessionService, never()).listByCourse(eq(assistantCourse.id()), eq(1), eq(4), eq(teacherId), eq(2));
        verify(questionBankService, never()).listQuestionBanksByCourse(assistantCourse.id());
    }

    private CourseVO course(UUID courseId, UUID teacherId, String title) {
        Instant now = Instant.now();
        return new CourseVO(
                courseId,
                title,
                null,
                teacherId,
                null,
                null,
                1,
                null,
                null,
                List.of(teacherId),
                null,
                null,
                null,
                1,
                100,
                32,
                20,
                1,
                now,
                now,
                4,
                25
        );
    }

    private EnrollmentVO enrollment(UUID courseId, int status, int progress) {
        return new EnrollmentVO(
                UUID.randomUUID(),
                courseId,
                "Course",
                null,
                UUID.randomUUID(),
                null,
                status,
                Instant.now(),
                null,
                32,
                4,
                progress
        );
    }

    private ClassSessionVO session(UUID courseId) {
        Instant now = Instant.now();
        return new ClassSessionVO(
                UUID.randomUUID(),
                courseId,
                UUID.randomUUID(),
                "Session",
                null,
                now.plusSeconds(3600),
                now.plusSeconds(5400),
                now,
                0,
                "room",
                ClassLiveStatus.NOT_STARTED.getCode(),
                "not started",
                null,
                null,
                null,
                1,
                "upcoming",
                false,
                now,
                now
        );
    }
}
