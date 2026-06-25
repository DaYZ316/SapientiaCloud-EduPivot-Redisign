package com.dayz.sc.course.service;

import com.dayz.sc.common.dashboard.DashboardChartPoint;
import com.dayz.sc.common.dashboard.DashboardNotificationItem;
import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.dashboard.DashboardUserActivity;
import com.dayz.sc.common.dashboard.DashboardUserSummary;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthDashboardInternalClient;
import com.dayz.sc.common.feign.client.NotificationDashboardInternalClient;
import com.dayz.sc.common.model.UserRole;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.course.model.dto.CoursePageRequest;
import com.dayz.sc.course.model.enums.ClassLiveStatus;
import com.dayz.sc.course.model.enums.ClassSessionStatus;
import com.dayz.sc.course.model.enums.CourseStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.entity.ClassSession;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.CourseVO;
import com.dayz.sc.course.model.vo.EnrollmentVO;
import com.dayz.sc.course.model.vo.PracticeSessionVO;
import com.dayz.sc.course.model.vo.QuestionBankVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardActionVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardAdminVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardCapacityItemVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardQuestionCoverageVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardResponseVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardRiskItemVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardStudentVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardSummaryItemVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardTeacherVO;
import com.dayz.sc.course.model.vo.dashboard.DashboardTimelineItemVO;
import com.dayz.sc.course.repository.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int ROLE_ADMIN = UserRole.ADMIN.getCode();
    private static final int ROLE_STUDENT = UserRole.STUDENT.getCode();
    private static final int ROLE_TEACHER = UserRole.TEACHER.getCode();
    private static final int SUMMARY_SIZE = 8;
    private static final int SESSION_DISPLAY_SIZE = 5;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final ClassSessionService classSessionService;
    private final ClassSessionRepository classSessionRepository;
    private final QuestionBankService questionBankService;
    private final PracticeSessionService practiceSessionService;
    private final AuthDashboardInternalClient authDashboardInternalClient;
    private final NotificationDashboardInternalClient notificationDashboardInternalClient;

    public DashboardResponseVO getDashboard(UUID userId, Integer role) {
        UserRole userRole = UserRole.fromCode(role);
        if (userRole == null) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        return switch (userRole) {
            case ADMIN -> new DashboardResponseVO(ROLE_ADMIN, buildAdminDashboard(userId), null, null);
            case STUDENT -> new DashboardResponseVO(ROLE_STUDENT, null, buildStudentDashboard(userId), null);
            case TEACHER -> new DashboardResponseVO(ROLE_TEACHER, null, null, buildTeacherDashboard(userId));
        };
    }

    private DashboardAdminVO buildAdminDashboard(UUID userId) {
        DashboardUserSummary users = loadUserSummary();
        DashboardNotificationSummary notifications = loadNotificationSummary(userId, true);
        PageResponse<CourseVO> coursesPage = listCourses(null, null, SUMMARY_SIZE);
        List<CourseVO> courses = coursesPage.records();
        long draftCourses = countCourses(CourseStatus.DRAFT.getCode(), null);
        long publishedCourses = countCourses(CourseStatus.PUBLISHED.getCode(), null);
        long archivedCourses = countCourses(CourseStatus.ARCHIVED.getCode(), null);
        long publicCourses = countCourses(null, 1);

        return new DashboardAdminVO(
                users,
                notifications,
                coursesPage.total(),
                publishedCourses,
                List.of(
                        new DashboardChartPoint("草稿", draftCourses),
                        new DashboardChartPoint("已发布", publishedCourses),
                        new DashboardChartPoint("已归档", archivedCourses)
                ),
                buildCapacityRanking(courses),
                List.of(
                        new DashboardSummaryItemVO("公开课程", formatNumber(publicCourses), "学生可发现"),
                        new DashboardSummaryItemVO("草稿课程", formatNumber(draftCourses), "待发布"),
                        new DashboardSummaryItemVO("归档课程", formatNumber(archivedCourses), "历史内容"),
                        new DashboardSummaryItemVO("课程报名量", formatNumber(sumStudents(courses)), "当前页摘要"),
                        new DashboardSummaryItemVO("课堂发布量", formatNumber(sumSessions(courses)), "当前页摘要")
                ),
                buildAdminActivities(users.recentUsers(), courses),
                buildAdminRisks(users, courses)
        );
    }

    private DashboardTeacherVO buildTeacherDashboard(UUID teacherId) {
        DashboardNotificationSummary notifications = loadNotificationSummary(teacherId, false);
        List<CourseVO> primaryCourses = courseService.listTeacherCourses(teacherId, "primary", 1, SUMMARY_SIZE).records();
        List<CourseVO> assistantCourses = courseService.listTeacherCourses(teacherId, "assistant", 1, 6).records();
        List<ClassSessionVO> sessions = loadSessions(primaryCourses.stream().map(CourseVO::id).limit(4).toList(), teacherId, ROLE_TEACHER, 4)
                .stream()
                .limit(SESSION_DISPLAY_SIZE)
                .toList();

        return new DashboardTeacherVO(
                notifications,
                primaryCourses,
                assistantCourses,
                primaryCourses.stream().limit(6).toList(),
                sessions,
                teacherOngoingSession(teacherId),
                buildTeacherPending(primaryCourses, notifications.recent()),
                buildTeacherInsights(primaryCourses),
                buildQuestionCoverage(primaryCourses),
                buildCapacityRanking(primaryCourses)
        );
    }

    private DashboardStudentVO buildStudentDashboard(UUID studentId) {
        DashboardNotificationSummary notifications = loadNotificationSummary(studentId, false);
        List<EnrollmentVO> enrollments = enrollmentService.listStudentEnrollments(studentId, 1, SUMMARY_SIZE).records();
        List<EnrollmentVO> accessibleEnrollments = enrollments.stream()
                .filter(this::activeOrCompleted)
                .toList();
        List<ClassSessionVO> sessions = loadSessions(accessibleEnrollments.stream()
                .map(EnrollmentVO::courseId)
                .limit(3)
                .toList(), studentId, ROLE_STUDENT, 4);
        List<ClassSessionVO> ongoingSessions = studentOngoingSessions(studentId);
        List<CourseVO> recommendations = listCourses(CourseStatus.PUBLISHED.getCode(), 1, 4).records();
        List<PracticeSessionVO> practiceSessions = latestPracticeSessions(studentId);

        return new DashboardStudentVO(
                notifications,
                enrollments,
                sessions,
                recommendations,
                ongoingSessions,
                firstSession(ongoingSessions),
                continueCourse(accessibleEnrollments),
                buildPracticeFocus(accessibleEnrollments),
                practiceSessions,
                buildStudentTodos(sessions, notifications.recent())
        );
    }

    private DashboardUserSummary loadUserSummary() {
        try {
            return unwrap(authDashboardInternalClient.getUserSummary(), DashboardUserSummary.empty());
        } catch (Exception ignored) {
            return DashboardUserSummary.empty();
        }
    }

    private DashboardNotificationSummary loadNotificationSummary(UUID userId, boolean includeDistribution) {
        try {
            return unwrap(notificationDashboardInternalClient.getSummary(userId, includeDistribution),
                    DashboardNotificationSummary.empty());
        } catch (Exception ignored) {
            return DashboardNotificationSummary.empty();
        }
    }

    private <T> T unwrap(ApiResponse<T> response, T fallback) {
        if (response == null || response.code() != ErrorCodes.SUCCESS.code() || response.data() == null) {
            return fallback;
        }
        return response.data();
    }

    private PageResponse<CourseVO> listCourses(Integer status, Integer isPublic, int size) {
        return courseService.listCourses(new CoursePageRequest(
                1L,
                (long) size,
                null,
                null,
                status,
                isPublic,
                null,
                null,
                null,
                null
        ));
    }

    private long countCourses(Integer status, Integer isPublic) {
        return listCourses(status, isPublic, 1).total();
    }

    private List<ClassSessionVO> loadSessions(List<UUID> courseIds, UUID userId, Integer role, int size) {
        return courseIds.stream()
                .flatMap(courseId -> safeSessions(courseId, userId, role, size).stream())
                .sorted(Comparator.comparing(ClassSessionVO::scheduledStartAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<ClassSessionVO> safeSessions(UUID courseId, UUID userId, Integer role, int size) {
        try {
            return classSessionService.listByCourse(courseId, 1, size, userId, role).records();
        } catch (BusinessException ignored) {
            return List.of();
        }
    }

    private ClassSessionVO firstSession(List<ClassSessionVO> sessions) {
        return sessions.stream()
                .findFirst()
                .orElse(null);
    }

    private ClassSessionVO teacherOngoingSession(UUID teacherId) {
        return classSessionRepository.findOngoingByTeacherId(teacherId, Instant.now(), 1).stream()
                .findFirst()
                .map(this::sessionVO)
                .orElse(null);
    }

    private List<ClassSessionVO> studentOngoingSessions(UUID studentId) {
        return classSessionRepository.findOngoingByStudentId(
                        studentId,
                        EnrollmentStatus.ACTIVE.getCode(),
                        EnrollmentStatus.COMPLETED.getCode(),
                        Instant.now(),
                        SESSION_DISPLAY_SIZE
                ).stream()
                .map(this::sessionVO)
                .toList();
    }

    private ClassSessionVO sessionVO(ClassSession session) {
        ClassLiveStatus liveStatus = ClassLiveStatus.fromCode(session.getLiveStatus());
        ClassSessionStatus status = ClassSessionStatus.calculate(
                session.getPublishedAt(),
                session.getScheduledStartAt(),
                session.getScheduledEndAt(),
                Instant.now()
        );
        return new ClassSessionVO(
                session.getId(),
                session.getCourseId(),
                session.getTeacherId(),
                session.getTitle(),
                session.getDescription(),
                session.getScheduledStartAt(),
                session.getScheduledEndAt(),
                session.getPublishedAt(),
                session.getRoomSize(),
                session.getLiveRoomName(),
                liveStatus.getCode(),
                liveStatus.getDescription(),
                session.getLiveStartedAt(),
                session.getLivePausedAt(),
                session.getLiveEndedAt(),
                status.getCode(),
                status.getDescription(),
                false,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private List<DashboardQuestionCoverageVO> buildQuestionCoverage(List<CourseVO> courses) {
        return courses.stream()
                .limit(6)
                .map(course -> {
                    List<QuestionBankVO> banks = questionBankService.listQuestionBanksByCourse(course.id());
                    long questions = banks.stream().mapToLong(QuestionBankVO::questionCount).sum();
                    return new DashboardQuestionCoverageVO(course.id(), course.title(), banks.size(), questions);
                })
                .toList();
    }

    private List<PracticeSessionVO> latestPracticeSessions(UUID studentId) {
        return practiceSessionService.getMyPracticeHistory(studentId).stream()
                .sorted(Comparator.comparing(PracticeSessionVO::startedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(SUMMARY_SIZE)
                .sorted(Comparator.comparing(PracticeSessionVO::startedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private EnrollmentVO continueCourse(List<EnrollmentVO> enrollments) {
        return enrollments.stream()
                .filter(enrollment -> enrollment.status() == EnrollmentStatus.ACTIVE.getCode())
                .findFirst()
                .or(() -> enrollments.stream().findFirst())
                .orElse(null);
    }

    private DashboardActionVO buildPracticeFocus(List<EnrollmentVO> enrollments) {
        EnrollmentVO lowProgress = enrollments.stream()
                .min(Comparator.comparingInt(enrollment -> clampPercent(enrollment.courseProgress())))
                .orElse(null);
        if (lowProgress == null) {
            return new DashboardActionVO(
                    "题库练习",
                    "选择一门公开课程或已选课程后，系统会推荐继续练习的题库。",
                    "/courses",
                    "Target"
            );
        }
        return new DashboardActionVO(
                safeTitle(lowProgress.courseTitle()) + " 需要巩固",
                "当前进度 " + clampPercent(lowProgress.courseProgress()) + "%，建议先完成章节题库和随堂练习。",
                "/courses/" + lowProgress.courseId() + "/banks",
                "Target"
        );
    }

    private List<DashboardTimelineItemVO> buildAdminActivities(List<DashboardUserActivity> users, List<CourseVO> courses) {
        List<DashboardTimelineItemVO> items = new ArrayList<>();
        users.stream().limit(3).forEach(user -> items.add(new DashboardTimelineItemVO(
                "新用户注册",
                user.name() != null ? user.name() : user.email(),
                formatInstant(user.createdAt()),
                "normal"
        )));
        courses.stream().limit(4).forEach(course -> items.add(new DashboardTimelineItemVO(
                course.status() == CourseStatus.PUBLISHED.getCode() ? "课程已发布" : "课程内容更新",
                course.title(),
                formatInstant(course.updatedAt() != null ? course.updatedAt() : course.createdAt()),
                "normal"
        )));
        return items.stream().limit(6).toList();
    }

    private List<DashboardTimelineItemVO> buildStudentTodos(List<ClassSessionVO> sessions, List<DashboardNotificationItem> notifications) {
        List<DashboardTimelineItemVO> items = new ArrayList<>();
        Instant now = Instant.now();
        sessions.stream()
                .filter(session -> !session.scheduledEndAt().isBefore(now))
                .limit(3)
                .forEach(session -> items.add(new DashboardTimelineItemVO(
                        session.status() == ClassSessionStatus.LIVE.getCode() ? "正在进行的课堂" : "即将开始的课堂",
                        session.title(),
                        formatInstant(session.scheduledStartAt()),
                        session.status() == ClassSessionStatus.LIVE.getCode() ? "live" : "normal"
                )));
        notifications.stream().limit(3).forEach(notification -> items.add(new DashboardTimelineItemVO(
                notification.type() != null && notification.type() == 1 ? "课程通知待查看" : "系统通知待查看",
                notification.title(),
                formatInstant(notification.createdAt()),
                notification.read() ? "normal" : "warning"
        )));
        return items;
    }

    private List<DashboardTimelineItemVO> buildTeacherPending(List<CourseVO> courses, List<DashboardNotificationItem> notifications) {
        List<DashboardTimelineItemVO> items = new ArrayList<>();
        courses.stream()
                .filter(course -> course.maxStudents() > 0 && capacityPercent(course) >= 80)
                .limit(2)
                .forEach(course -> items.add(new DashboardTimelineItemVO(
                        "课程容量接近上限",
                        course.title() + " 当前 " + course.currentStudents() + "/" + course.maxStudents(),
                        "容量",
                        "warning"
                )));
        notifications.stream().limit(4).forEach(notification -> items.add(new DashboardTimelineItemVO(
                notification.type() != null && notification.type() == 1 ? "教学通知待处理" : "通知待查看",
                notification.title(),
                formatInstant(notification.createdAt()),
                notification.read() ? "normal" : "warning"
        )));
        return items.stream().limit(6).toList();
    }

    private List<DashboardSummaryItemVO> buildTeacherInsights(List<CourseVO> courses) {
        long students = sumStudents(courses);
        long capacity = courses.stream().mapToLong(CourseVO::maxStudents).sum();
        int progress = courses.isEmpty()
                ? 0
                : (int) Math.round(courses.stream().mapToInt(course -> clampPercent(course.courseProgress())).average().orElse(0));
        return List.of(
                new DashboardSummaryItemVO("课程参与容量", capacity > 0 ? Math.round(students * 100.0 / capacity) + "%" : "--", "主讲课程"),
                new DashboardSummaryItemVO("平均课程进度", progress + "%", "已发布课堂"),
                new DashboardSummaryItemVO("低活跃课程", formatNumber(courses.stream().filter(course -> clampPercent(course.courseProgress()) < 35).count()), "进度低于 35%"),
                new DashboardSummaryItemVO("课堂发布密度", formatNumber(sumSessions(courses)), "已发布课堂")
        );
    }

    private List<DashboardRiskItemVO> buildAdminRisks(DashboardUserSummary users, List<CourseVO> courses) {
        boolean capacityRisk = courses.stream().anyMatch(course -> course.maxStudents() > 0 && capacityPercent(course) >= 85);
        return List.of(
                new DashboardRiskItemVO(
                        "禁用账号复核",
                        users.disabledUsers() > 0 ? users.disabledUsers() + " 个账号需要复核" : "暂无禁用账号进入审计列表",
                        users.disabledUsers() > 0 ? "需处理" : "正常",
                        users.disabledUsers() > 0 ? "warning" : "normal",
                        "ShieldCheck"
                ),
                new DashboardRiskItemVO(
                        "资料完整度",
                        users.incompleteProfiles() > 0 ? users.incompleteProfiles() + " 个账号资料未完善" : "近期账号资料状态稳定",
                        users.incompleteProfiles() > 0 ? "关注" : "正常",
                        users.incompleteProfiles() > 0 ? "warning" : "normal",
                        "AlertTriangle"
                ),
                new DashboardRiskItemVO(
                        "课程容量",
                        capacityRisk ? findCapacityRisk(courses) : "当前课程容量状态稳定",
                        capacityRisk ? "接近上限" : "正常",
                        capacityRisk ? "critical" : "normal",
                        "Users"
                )
        );
    }

    private List<DashboardCapacityItemVO> buildCapacityRanking(List<CourseVO> courses) {
        return courses.stream()
                .filter(course -> course.maxStudents() > 0)
                .sorted(Comparator.comparingInt(this::capacityPercent).reversed())
                .limit(5)
                .map(course -> new DashboardCapacityItemVO(
                        course.id(),
                        course.title(),
                        capacityPercent(course),
                        course.currentStudents() + "/" + course.maxStudents() + " 名学生"
                ))
                .toList();
    }

    private boolean activeOrCompleted(EnrollmentVO enrollment) {
        return enrollment.status() == EnrollmentStatus.ACTIVE.getCode()
                || enrollment.status() == EnrollmentStatus.COMPLETED.getCode();
    }

    private String findCapacityRisk(List<CourseVO> courses) {
        return courses.stream()
                .filter(course -> course.maxStudents() > 0 && capacityPercent(course) >= 85)
                .findFirst()
                .map(course -> course.title() + " 已达到 " + course.currentStudents() + "/" + course.maxStudents())
                .orElse("当前课程容量状态稳定");
    }

    private long sumStudents(List<CourseVO> courses) {
        return courses.stream().mapToLong(CourseVO::currentStudents).sum();
    }

    private long sumSessions(List<CourseVO> courses) {
        return courses.stream().mapToLong(CourseVO::publishedClassSessionCount).sum();
    }

    private int capacityPercent(CourseVO course) {
        if (course.maxStudents() <= 0) {
            return 0;
        }
        return clampPercent(course.currentStudents() * 100.0 / course.maxStudents());
    }

    private int clampPercent(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round(value)));
    }

    private String safeTitle(String title) {
        return title == null || title.isBlank() ? "课程" : title;
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "-" : TIME_FORMATTER.format(instant);
    }

    private String formatNumber(long value) {
        return Long.toString(value);
    }
}
