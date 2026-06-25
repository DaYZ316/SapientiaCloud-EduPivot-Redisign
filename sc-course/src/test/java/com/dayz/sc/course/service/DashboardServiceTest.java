package com.dayz.sc.course.service;

import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthDashboardInternalClient;
import com.dayz.sc.common.feign.client.NotificationDashboardInternalClient;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.course.model.dto.CoursePageRequest;
import com.dayz.sc.course.model.entity.ClassSession;
import com.dayz.sc.course.model.enums.ClassLiveStatus;
import com.dayz.sc.course.model.enums.ClassSessionStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.course.model.vo.EnrollmentVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardResponseVO;
import com.dayz.sc.course.repository.ClassSessionRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
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
    private ClassSessionRepository classSessionRepository;

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
                classSessionRepository,
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
        when(classSessionRepository.findOngoingByStudentId(
                eq(studentId),
                eq(EnrollmentStatus.ACTIVE.getCode()),
                eq(EnrollmentStatus.COMPLETED.getCode()),
                any(Instant.class),
                eq(5)
        )).thenReturn(List.of());
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
        when(classSessionRepository.findOngoingByTeacherId(eq(teacherId), any(Instant.class), eq(1))).thenReturn(List.of());
        when(questionBankService.listQuestionBanksByCourse(primaryCourse.id())).thenReturn(List.of());

        DashboardResponseVO response = dashboardService.getDashboard(teacherId, 2);

        assertThat(response.teacher()).isNotNull();
        assertThat(response.teacher().primaryCourses()).containsExactly(primaryCourse);
        assertThat(response.teacher().assistantCourses()).containsExactly(assistantCourse);
        verify(classSessionService, never()).listByCourse(eq(assistantCourse.id()), anyInt(), anyInt(), eq(teacherId), eq(2));
        verify(questionBankService, never()).listQuestionBanksByCourse(assistantCourse.id());
    }

    @Test
    void getDashboard_shouldDetectTeacherOngoingSessionOutsideDisplayedSlice() {
        UUID teacherId = UUID.randomUUID();
        CourseVO primaryCourse = course(UUID.randomUUID(), teacherId, "Primary");
        Instant startAt = Instant.now();
        ClassSession ongoingSession = ongoingSession(primaryCourse.id(), teacherId, "Ongoing session");
        List<ClassSessionVO> sessions = List.of(
                session(primaryCourse.id(), "Session 1", startAt.plusSeconds(60), ClassLiveStatus.NOT_STARTED.getCode()),
                session(primaryCourse.id(), "Session 2", startAt.plusSeconds(120), ClassLiveStatus.NOT_STARTED.getCode()),
                session(primaryCourse.id(), "Session 3", startAt.plusSeconds(180), ClassLiveStatus.NOT_STARTED.getCode()),
                session(primaryCourse.id(), "Session 4", startAt.plusSeconds(240), ClassLiveStatus.NOT_STARTED.getCode()),
                session(primaryCourse.id(), "Session 5", startAt.plusSeconds(300), ClassLiveStatus.NOT_STARTED.getCode())
        );

        when(notificationDashboardInternalClient.getSummary(teacherId, false))
                .thenReturn(ApiResponse.ok(DashboardNotificationSummary.empty()));
        when(courseService.listTeacherCourses(teacherId, "primary", 1, 8))
                .thenReturn(PageResponse.of(List.of(primaryCourse), 1, 1, 8));
        when(courseService.listTeacherCourses(teacherId, "assistant", 1, 6))
                .thenReturn(PageResponse.empty(1, 6));
        when(classSessionService.listByCourse(primaryCourse.id(), 1, 4, teacherId, 2))
                .thenReturn(PageResponse.of(sessions, sessions.size(), 1, 4));
        when(classSessionRepository.findOngoingByTeacherId(eq(teacherId), any(Instant.class), eq(1)))
                .thenReturn(List.of(ongoingSession));
        when(questionBankService.listQuestionBanksByCourse(primaryCourse.id())).thenReturn(List.of());

        DashboardResponseVO response = dashboardService.getDashboard(teacherId, 2);

        assertThat(response.teacher()).isNotNull();
        assertThat(response.teacher().liveSession().id()).isEqualTo(ongoingSession.getId());
        assertThat(response.teacher().liveSession().status()).isEqualTo(ClassSessionStatus.LIVE.getCode());
        assertThat(response.teacher().liveSession().liveStatus()).isEqualTo(ClassLiveStatus.NOT_STARTED.getCode());
        assertThat(response.teacher().sessions()).hasSize(5);
    }

    @Test
    void getDashboard_shouldDetectTeacherOngoingSessionOutsideDisplayedCourses() {
        UUID teacherId = UUID.randomUUID();
        List<CourseVO> primaryCourses = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(index -> course(UUID.randomUUID(), teacherId, "Primary " + index))
                .toList();
        CourseVO ongoingCourse = primaryCourses.get(8);
        ClassSession ongoingSession = ongoingSession(ongoingCourse.id(), teacherId, "Ongoing session");

        when(notificationDashboardInternalClient.getSummary(teacherId, false))
                .thenReturn(ApiResponse.ok(DashboardNotificationSummary.empty()));
        when(courseService.listTeacherCourses(teacherId, "primary", 1, 8))
                .thenReturn(PageResponse.of(primaryCourses.subList(0, 8), primaryCourses.size(), 1, 8));
        when(courseService.listTeacherCourses(teacherId, "assistant", 1, 6))
                .thenReturn(PageResponse.empty(1, 6));
        primaryCourses.subList(0, 4).forEach(course ->
                when(classSessionService.listByCourse(course.id(), 1, 4, teacherId, 2)).thenReturn(PageResponse.empty(1, 4)));
        when(classSessionRepository.findOngoingByTeacherId(eq(teacherId), any(Instant.class), eq(1)))
                .thenReturn(List.of(ongoingSession));
        primaryCourses.stream()
                .limit(6)
                .forEach(course -> when(questionBankService.listQuestionBanksByCourse(course.id())).thenReturn(List.of()));

        DashboardResponseVO response = dashboardService.getDashboard(teacherId, 2);

        assertThat(response.teacher()).isNotNull();
        assertThat(response.teacher().primaryCourses()).containsExactlyElementsOf(primaryCourses.subList(0, 8));
        assertThat(response.teacher().liveSession().id()).isEqualTo(ongoingSession.getId());
    }

    @Test
    void getDashboard_shouldReturnStudentOngoingSessionsAsList() {
        UUID studentId = UUID.randomUUID();
        UUID activeCourseId = UUID.randomUUID();
        ClassSession firstSession = ongoingSession(activeCourseId, UUID.randomUUID(), "First ongoing session");
        ClassSession secondSession = ongoingSession(activeCourseId, UUID.randomUUID(), "Second ongoing session");

        when(notificationDashboardInternalClient.getSummary(studentId, false))
                .thenReturn(ApiResponse.ok(DashboardNotificationSummary.empty()));
        when(enrollmentService.listStudentEnrollments(studentId, 1, 8))
                .thenReturn(PageResponse.of(List.of(enrollment(activeCourseId, EnrollmentStatus.ACTIVE.getCode(), 50)), 1, 1, 8));
        when(classSessionService.listByCourse(activeCourseId, 1, 4, studentId, 1))
                .thenReturn(PageResponse.empty(1, 4));
        when(classSessionRepository.findOngoingByStudentId(
                eq(studentId),
                eq(EnrollmentStatus.ACTIVE.getCode()),
                eq(EnrollmentStatus.COMPLETED.getCode()),
                any(Instant.class),
                eq(5)
        )).thenReturn(List.of(firstSession, secondSession));
        when(courseService.listCourses(any(CoursePageRequest.class))).thenReturn(PageResponse.empty(1, 4));
        when(practiceSessionService.getMyPracticeHistory(studentId)).thenReturn(List.of());

        DashboardResponseVO response = dashboardService.getDashboard(studentId, 1);

        assertThat(response.student()).isNotNull();
        assertThat(response.student().ongoingSessions())
                .extracting(ClassSessionVO::id)
                .containsExactly(firstSession.getId(), secondSession.getId());
        assertThat(response.student().liveSession().id()).isEqualTo(firstSession.getId());
        assertThat(response.student().liveSession().liveStatus()).isEqualTo(ClassLiveStatus.NOT_STARTED.getCode());
        assertThat(response.student().liveSession().status()).isEqualTo(ClassSessionStatus.LIVE.getCode());
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
        return session(courseId, "Session", now.plusSeconds(3600), ClassLiveStatus.NOT_STARTED.getCode());
    }

    private ClassSessionVO session(UUID courseId, String title, Instant startAt, int liveStatus) {
        Instant now = Instant.now();
        return new ClassSessionVO(
                UUID.randomUUID(),
                courseId,
                UUID.randomUUID(),
                title,
                null,
                startAt,
                startAt.plusSeconds(1800),
                now,
                0,
                "room",
                liveStatus,
                liveStatus == ClassLiveStatus.LIVE.getCode() ? "live" : "not started",
                null,
                null,
                null,
                liveStatus == ClassLiveStatus.LIVE.getCode() ? 2 : 1,
                liveStatus == ClassLiveStatus.LIVE.getCode() ? "live" : "upcoming",
                false,
                now,
                now
        );
    }

    private ClassSession ongoingSession(UUID courseId, UUID teacherId, String title) {
        Instant now = Instant.now();
        ClassSession session = new ClassSession();
        session.setId(UUID.randomUUID());
        session.setCourseId(courseId);
        session.setTeacherId(teacherId);
        session.setTitle(title);
        session.setScheduledStartAt(now.minusSeconds(60));
        session.setScheduledEndAt(now.plusSeconds(1800));
        session.setPublishedAt(now.minusSeconds(120));
        session.setRoomSize(0);
        session.setLiveRoomName("room");
        session.setLiveStatus(ClassLiveStatus.NOT_STARTED.getCode());
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }
}
