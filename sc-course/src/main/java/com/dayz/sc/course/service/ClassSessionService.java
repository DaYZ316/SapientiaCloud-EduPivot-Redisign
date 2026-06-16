package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.CreateClassBarrageRequest;
import com.dayz.sc.course.model.dto.CreateClassSessionRequest;
import com.dayz.sc.course.model.dto.JoinClassSessionRequest;
import com.dayz.sc.course.model.dto.UpdateClassSessionRequest;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.model.enums.ClassParticipantRole;
import com.dayz.sc.course.model.enums.ClassRoomSize;
import com.dayz.sc.course.model.enums.ClassSessionStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.ClassBarrageVO;
import com.dayz.sc.course.model.vo.ClassParticipantVO;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.LiveKitTokenVO;
import com.dayz.sc.course.repository.*;
import com.dayz.sc.course.sse.ClassBarrageSseEmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Business service for class sessions.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Service
@RequiredArgsConstructor
public class ClassSessionService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ClassSessionRepository classSessionRepository;
    private final ClassParticipantRepository classParticipantRepository;
    private final ClassBarrageRepository classBarrageRepository;
    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassBarrageSseEmitter barrageSseEmitter;
    private final LiveKitTokenService liveKitTokenService;

    @Transactional(rollbackFor = Exception.class)
    public UUID createSession(CreateClassSessionRequest request, UUID userId, Integer role) {
        if (!SecurityUtils.isTeacherOrAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireCourseTeacher(course, userId, role);
        validateTimeRange(request.scheduledStartAt(), request.scheduledEndAt());
        ClassRoomSize.fromCode(request.roomSize());

        ClassSession session = new ClassSession();
        session.setId(UuidV7Generator.generate());
        session.setCourseId(request.courseId());
        session.setTeacherId(userId);
        session.setTitle(request.title());
        session.setDescription(request.description());
        session.setScheduledStartAt(request.scheduledStartAt());
        session.setScheduledEndAt(request.scheduledEndAt());
        session.setRoomSize(request.roomSize());
        session.setLiveRoomName(roomName(session.getId()));
        classSessionRepository.save(session);
        return session.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateSession(UUID sessionId, UpdateClassSessionRequest request, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        if (session.getPublishedAt() != null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Published class sessions cannot be changed");
        }

        if (request.title() != null) {
            session.setTitle(request.title());
        }
        if (request.description() != null) {
            session.setDescription(request.description());
        }
        if (request.scheduledStartAt() != null) {
            session.setScheduledStartAt(request.scheduledStartAt());
        }
        if (request.scheduledEndAt() != null) {
            session.setScheduledEndAt(request.scheduledEndAt());
        }
        if (request.roomSize() != null) {
            ClassRoomSize.fromCode(request.roomSize());
            session.setRoomSize(request.roomSize());
        }
        validateTimeRange(session.getScheduledStartAt(), session.getScheduledEndAt());
        classSessionRepository.update(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public void publishSession(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        if (session.getPublishedAt() != null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class session is already published");
        }
        validateTimeRange(session.getScheduledStartAt(), session.getScheduledEndAt());
        session.setPublishedAt(Instant.now());
        classSessionRepository.update(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        classSessionRepository.deleteById(sessionId);
    }

    public PageResponse<ClassSessionVO> listByCourse(UUID courseId, int page, int size, UUID userId, Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        boolean isTeacher = isCourseTeacher(course.getId(), userId, role);
        boolean isStudent = isStudentEnrolled(course.getId(), userId);
        if (!isTeacher && !isStudent && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        int currentPage = PageUtils.normalizePage(page);
        int pageSize = PageUtils.normalizeSize(size);
        Page<ClassSession> result = classSessionRepository.findByCourseId(courseId, currentPage, pageSize, isTeacher || SecurityUtils.isAdmin(role));
        return PageResponse.of(result.getRecords().stream()
                .map(session -> toSessionVO(session, joined(session.getId(), userId)))
                .toList(), result.getTotal(), currentPage, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassSessionVO getSession(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        boolean isTeacher = isCourseTeacher(session.getCourseId(), userId, role);
        boolean isStudent = isStudentEnrolled(session.getCourseId(), userId);
        if (session.getPublishedAt() == null && !isTeacher && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        if (session.getPublishedAt() != null && !isTeacher && !isStudent && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        if (isTeacher && session.getPublishedAt() != null) {
            ensureParticipant(session, userId, ClassParticipantRole.TEACHER, ZERO, ZERO, ZERO);
        }
        return toSessionVO(session, joined(sessionId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassParticipantVO joinSession(UUID sessionId, JoinClassSessionRequest request, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensurePublished(session);
        boolean isTeacher = isCourseTeacher(session.getCourseId(), userId, role);
        if (isTeacher) {
            return toParticipantVO(ensureParticipant(session, userId, ClassParticipantRole.TEACHER,
                    valueOrZero(request.x()), valueOrZero(request.y()), valueOrZero(request.z())));
        }
        if (!SecurityUtils.isStudent(role) || !isStudentEnrolled(session.getCourseId(), userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        return toParticipantVO(ensureParticipant(session, userId, ClassParticipantRole.STUDENT,
                valueOrZero(request.x()), valueOrZero(request.y()), valueOrZero(request.z())));
    }

    public LiveKitTokenVO createLiveToken(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensurePublished(session);
        if (isCourseTeacher(session.getCourseId(), userId, role)) {
            ensureParticipant(session, userId, ClassParticipantRole.TEACHER, ZERO, ZERO, ZERO);
        }
        if (!joined(sessionId, userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        return liveKitTokenService.createToken(userId, session.getLiveRoomName(), isCourseTeacher(session.getCourseId(), userId, role));
    }

    public SseEmitter streamBarrages(UUID sessionId, UUID userId, Integer role) {
        requireJoinedPublishedSession(sessionId, userId, role);
        return barrageSseEmitter.createEmitter(sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassBarrageVO sendBarrage(UUID sessionId, CreateClassBarrageRequest request, UUID userId, Integer role) {
        requireJoinedPublishedSession(sessionId, userId, role);
        String content = request.content().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Barrage content cannot be blank");
        }

        ClassBarrage barrage = new ClassBarrage();
        barrage.setId(UuidV7Generator.generate());
        barrage.setSessionId(sessionId);
        barrage.setSenderId(userId);
        barrage.setContent(content);
        barrage.setSentAt(Instant.now());
        classBarrageRepository.save(barrage);
        ClassBarrageVO vo = toBarrageVO(barrage);
        barrageSseEmitter.broadcast(sessionId, vo);
        return vo;
    }

    public PageResponse<ClassBarrageVO> listBarrages(UUID sessionId, int page, int size, UUID userId, Integer role) {
        requireJoinedPublishedSession(sessionId, userId, role);
        int currentPage = PageUtils.normalizePage(page);
        int pageSize = PageUtils.normalizeSize(size);
        Page<ClassBarrage> result = classBarrageRepository.findBySessionId(sessionId, currentPage, pageSize);
        return PageResponse.of(result.getRecords().stream().map(this::toBarrageVO).toList(), result.getTotal(), currentPage, pageSize);
    }

    private void requireJoinedPublishedSession(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensurePublished(session);
        if (isCourseTeacher(session.getCourseId(), userId, role)) {
            ensureParticipant(session, userId, ClassParticipantRole.TEACHER, ZERO, ZERO, ZERO);
        }
        if (!joined(sessionId, userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private ClassParticipant ensureParticipant(ClassSession session, UUID userId, ClassParticipantRole role,
                                               BigDecimal x, BigDecimal y, BigDecimal z) {
        return classParticipantRepository.findBySessionIdAndUserId(session.getId(), userId)
                .map(existing -> {
                    existing.setRole(role.getCode());
                    existing.setX(x);
                    existing.setY(y);
                    existing.setZ(z);
                    classParticipantRepository.update(existing);
                    return existing;
                })
                .orElseGet(() -> {
                    ClassParticipant participant = new ClassParticipant();
                    participant.setId(UuidV7Generator.generate());
                    participant.setSessionId(session.getId());
                    participant.setUserId(userId);
                    participant.setRole(role.getCode());
                    participant.setX(x);
                    participant.setY(y);
                    participant.setZ(z);
                    participant.setJoinedAt(Instant.now());
                    classParticipantRepository.save(participant);
                    return participant;
                });
    }

    private void requireSessionTeacher(ClassSession session, UUID userId, Integer role) {
        if (!isCourseTeacher(session.getCourseId(), userId, role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private void requireCourseTeacher(Course course, UUID userId, Integer role) {
        if (!isCourseTeacher(course.getId(), userId, role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private boolean isCourseTeacher(UUID courseId, UUID userId, Integer role) {
        return userId != null && (SecurityUtils.isAdmin(role) || courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId));
    }

    private boolean isStudentEnrolled(UUID courseId, UUID userId) {
        if (userId == null) {
            return false;
        }
        return enrollmentRepository.findByCourseIdAndStudentId(courseId, userId)
                .map(this::activeOrCompleted)
                .orElse(false);
    }

    private boolean activeOrCompleted(Enrollment enrollment) {
        return enrollment.getStatus() == EnrollmentStatus.ACTIVE.getCode()
                || enrollment.getStatus() == EnrollmentStatus.COMPLETED.getCode();
    }

    private void ensurePublished(ClassSession session) {
        if (session.getPublishedAt() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class session is still preparing");
        }
    }

    private boolean joined(UUID sessionId, UUID userId) {
        return userId != null && classParticipantRepository.existsBySessionIdAndUserId(sessionId, userId);
    }

    private void validateTimeRange(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class session end time must be after start time");
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private String roomName(UUID sessionId) {
        return "class-session-" + sessionId;
    }

    private ClassSessionVO toSessionVO(ClassSession session, boolean joined) {
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
                status.getCode(),
                status.getDescription(),
                joined,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private ClassParticipantVO toParticipantVO(ClassParticipant participant) {
        return new ClassParticipantVO(
                participant.getId(),
                participant.getSessionId(),
                participant.getUserId(),
                participant.getRole(),
                participant.getX(),
                participant.getY(),
                participant.getZ(),
                participant.getJoinedAt()
        );
    }

    private ClassBarrageVO toBarrageVO(ClassBarrage barrage) {
        return new ClassBarrageVO(
                barrage.getId(),
                barrage.getSessionId(),
                barrage.getSenderId(),
                barrage.getContent(),
                barrage.getSentAt()
        );
    }
}
