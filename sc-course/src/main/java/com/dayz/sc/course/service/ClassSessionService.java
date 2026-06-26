package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.CreateClassBarrageRequest;
import com.dayz.sc.course.model.dto.CreateClassSessionRequest;
import com.dayz.sc.course.model.dto.JoinClassSessionRequest;
import com.dayz.sc.course.model.dto.UpdateClassSessionRequest;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.model.enums.ClassLiveStatus;
import com.dayz.sc.course.model.enums.ClassParticipantRole;
import com.dayz.sc.course.model.enums.ClassRoomSize;
import com.dayz.sc.course.model.enums.ClassSessionStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.ClassBarrageVO;
import com.dayz.sc.course.model.vo.ClassParticipantVO;
import com.dayz.sc.course.model.vo.ClassSeatSyncTokenVO;
import com.dayz.sc.course.model.vo.ClassSessionVO;
import com.dayz.sc.course.model.vo.LiveKitTokenVO;
import com.dayz.sc.course.repository.*;
import com.dayz.sc.course.sse.ClassBarrageSseEmitter;
import com.dayz.sc.course.websocket.ClassSeatSyncTokenService;
import com.dayz.sc.course.websocket.ClassSeatSyncWebSocketHub;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private static final int SMALL_SEAT_COUNT = 12;
    private static final int MEDIUM_SEAT_COUNT = 64;
    private static final int LARGE_SEAT_COUNT = 160;
    private static final int XLARGE_SEAT_COUNT = 250;
    private static final Duration MAX_SESSION_DURATION = Duration.ofHours(2);

    private final ClassSessionRepository classSessionRepository;
    private final ClassParticipantRepository classParticipantRepository;
    private final ClassBarrageRepository classBarrageRepository;
    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ClassBarrageSseEmitter barrageSseEmitter;
    private final LiveKitTokenService liveKitTokenService;
    private final LiveKitRoomService liveKitRoomService;
    private final ClassroomUserInfoResolver classroomUserInfoResolver;
    private final ClassSeatSyncTokenService classSeatSyncTokenService;
    private final ClassSeatSyncWebSocketHub seatSyncWebSocketHub;
    private final ClassLivePresenceService classLivePresenceService;

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
        session.setLiveStatus(ClassLiveStatus.NOT_STARTED.getCode());
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

    public PageResponse<@NonNull ClassSessionVO> listByCourse(UUID courseId, int page, int size, UUID userId, Integer role) {
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
            ensureParticipant(session, userId, ClassParticipantRole.TEACHER, null, ZERO, ZERO, ZERO);
        }
        return toSessionVO(session, joined(sessionId, userId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassParticipantVO joinSession(UUID sessionId, JoinClassSessionRequest request, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensurePublished(session);
        if (!SecurityUtils.isStudent(role) || !isStudentEnrolled(session.getCourseId(), userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        int seatIndex = validateSeatIndex(session.getRoomSize(), request.seatIndex());
        ClassParticipant existing = classParticipantRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElse(null);
        if (existing != null && Integer.valueOf(seatIndex).equals(existing.getSeatIndex())) {
            return toParticipantVO(existing, loadUserInfoMap(List.of(existing.getUserId())));
        }
        ensureSeatAvailable(sessionId, seatIndex, userId);
        try {
            ClassParticipant participant = saveStudentSeat(session, existing, userId, seatIndex,
                    valueOrZero(request.x()), valueOrZero(request.y()), valueOrZero(request.z()));
            ClassParticipantVO vo = toParticipantVO(participant, loadUserInfoMap(List.of(participant.getUserId())));
            seatSyncWebSocketHub.broadcastUpsert(sessionId, vo);
            return vo;
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Seat is already occupied");
        }
    }

    public List<ClassParticipantVO> listParticipants(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requirePublishedSessionAccess(session, userId, role);
        List<ClassParticipant> participants = classParticipantRepository.findBySessionId(sessionId);
        Map<UUID, UserBasicInfo> userInfoMap = loadUserInfoMap(participants.stream()
                .map(ClassParticipant::getUserId)
                .distinct()
                .toList());
        return participants.stream()
                .map(participant -> toParticipantVO(participant, userInfoMap))
                .toList();
    }

    public ClassSeatSyncTokenVO createSeatSyncToken(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requirePublishedSessionAccess(session, userId, role);
        return classSeatSyncTokenService.issueToken(sessionId, userId, role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void leaveSeat(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensurePublished(session);
        if (!SecurityUtils.isStudent(role) || !isStudentEnrolled(session.getCourseId(), userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        classParticipantRepository.findBySessionIdAndUserId(sessionId, userId)
                .ifPresent(participant -> {
                    Integer seatIndex = participant.getSeatIndex();
                    classParticipantRepository.deleteBySessionIdAndUserId(sessionId, userId);
                    seatSyncWebSocketHub.broadcastRemove(sessionId, userId, seatIndex);
                });
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassSessionVO startLive(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        liveKitTokenService.requireConfigured();
        ensureClassOngoing(session);
        ClassLiveStatus status = liveStatus(session);
        if (status != ClassLiveStatus.NOT_STARTED && status != ClassLiveStatus.ENDED) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Live stream cannot be started from current status");
        }

        Instant now = Instant.now();
        session.setLiveStatus(ClassLiveStatus.LIVE.getCode());
        session.setLiveStartedAt(now);
        session.setLivePausedAt(null);
        session.setLiveEndedAt(null);
        classSessionRepository.update(session);
        classLivePresenceService.heartbeat(sessionId, userId);
        ensureParticipant(session, userId, ClassParticipantRole.TEACHER, null, ZERO, ZERO, ZERO);
        ClassSessionVO vo = toSessionVO(session, true);
        seatSyncWebSocketHub.broadcastLiveStatus(sessionId, vo, "live_started");
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassSessionVO pauseLive(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        liveKitTokenService.requireConfigured();
        if (liveStatus(session) != ClassLiveStatus.LIVE) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Only live streams can be paused");
        }

        session.setLiveStatus(ClassLiveStatus.PAUSED.getCode());
        session.setLivePausedAt(Instant.now());
        classSessionRepository.update(session);
        ClassSessionVO vo = toSessionVO(session, joined(sessionId, userId));
        seatSyncWebSocketHub.broadcastLiveStatus(sessionId, vo, "live_paused");
        return vo;
    }

    public void heartbeatLive(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        if (liveStatus(session) == ClassLiveStatus.LIVE) {
            classLivePresenceService.heartbeat(sessionId, userId);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassSessionVO resumeLive(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        liveKitTokenService.requireConfigured();
        ensureClassOngoing(session);
        if (liveStatus(session) != ClassLiveStatus.PAUSED) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Only paused live streams can be resumed");
        }

        session.setLiveStatus(ClassLiveStatus.LIVE.getCode());
        session.setLivePausedAt(null);
        classSessionRepository.update(session);
        classLivePresenceService.heartbeat(sessionId, userId);
        ClassSessionVO vo = toSessionVO(session, joined(sessionId, userId));
        seatSyncWebSocketHub.broadcastLiveStatus(sessionId, vo, "live_resumed");
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassSessionVO stopLive(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireSessionTeacher(session, userId, role);
        liveKitTokenService.requireConfigured();
        ClassLiveStatus status = liveStatus(session);
        if (status != ClassLiveStatus.LIVE && status != ClassLiveStatus.PAUSED) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Only active live streams can be stopped");
        }

        session.setLiveStatus(ClassLiveStatus.ENDED.getCode());
        session.setLivePausedAt(null);
        session.setLiveEndedAt(Instant.now());
        classSessionRepository.update(session);
        liveKitRoomService.deleteRoom(session.getLiveRoomName());
        ClassSessionVO vo = toSessionVO(session, joined(sessionId, userId));
        seatSyncWebSocketHub.broadcastLiveStatus(sessionId, vo, "live_stopped");
        return vo;
    }

    public LiveKitTokenVO createLiveToken(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensureClassOngoing(session);
        ClassLiveStatus liveStatus = liveStatus(session);
        boolean isTeacher = isCourseTeacher(session.getCourseId(), userId, role);
        if (isTeacher) {
            if (liveStatus == ClassLiveStatus.ENDED) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Live stream has ended");
            }
            ensureParticipant(session, userId, ClassParticipantRole.TEACHER, null, ZERO, ZERO, ZERO);
            return liveKitTokenService.createToken(userId, session.getLiveRoomName(), true);
        }
        if (liveStatus != ClassLiveStatus.LIVE && liveStatus != ClassLiveStatus.PAUSED) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Live stream is not available");
        }
        requireStudentSeat(session, userId, role);
        return liveKitTokenService.createToken(userId, session.getLiveRoomName(), false);
    }

    public SseEmitter streamBarrages(UUID sessionId, UUID userId, Integer role) {
        requireClassroomInteractionAccess(sessionId, userId, role);
        return barrageSseEmitter.createEmitter(sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ClassBarrageVO sendBarrage(UUID sessionId, CreateClassBarrageRequest request, UUID userId, Integer role) {
        ClassroomInteractionAccess access = requireClassroomInteractionAccess(sessionId, userId, role);
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
        ClassBarrageVO vo = toBarrageVO(
                barrage,
                access.session(),
                loadUserInfoMap(List.of(userId)),
                Map.of(userId, access.participantRole())
        );
        barrageSseEmitter.broadcast(sessionId, vo);
        return vo;
    }

    public PageResponse<@NonNull ClassBarrageVO> listBarrages(UUID sessionId, int page, int size, UUID userId, Integer role) {
        ClassSession session = requireJoinedPublishedSession(sessionId, userId, role);
        int currentPage = PageUtils.normalizePage(page);
        int pageSize = PageUtils.normalizeSize(size);
        Page<ClassBarrage> result = classBarrageRepository.findBySessionId(sessionId, currentPage, pageSize);
        List<UUID> senderIds = result.getRecords().stream()
                .map(ClassBarrage::getSenderId)
                .distinct()
                .toList();
        Map<UUID, UserBasicInfo> userInfoMap = loadUserInfoMap(senderIds);
        Map<UUID, Integer> participantRoleMap = loadParticipantRoleMap(sessionId, senderIds);
        return PageResponse.of(
                result.getRecords().stream()
                        .map(barrage -> toBarrageVO(barrage, session, userInfoMap, participantRoleMap))
                        .toList(),
                result.getTotal(),
                currentPage,
                pageSize
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public int endExpiredLiveSessions() {
        Instant now = Instant.now();
        List<ClassSession> sessions = classSessionRepository.findLiveSessionsPastEnd(
                now,
                ClassLiveStatus.ENDED.getCode(),
                100
        );
        int endedCount = 0;
        for (ClassSession session : sessions) {
            if (liveStatus(session) == ClassLiveStatus.ENDED) {
                continue;
            }
            session.setLiveStatus(ClassLiveStatus.ENDED.getCode());
            session.setLivePausedAt(null);
            session.setLiveEndedAt(now);
            classSessionRepository.update(session);
            liveKitRoomService.deleteRoom(session.getLiveRoomName());
            seatSyncWebSocketHub.broadcastLiveStatus(session.getId(), toSessionVO(session, false), "live_stopped");
            endedCount++;
        }
        return endedCount;
    }

    @Transactional(rollbackFor = Exception.class)
    public int pauseDisconnectedLiveSessions() {
        List<ClassSession> sessions = classSessionRepository.findLiveSessions(ClassLiveStatus.LIVE.getCode(), 100);
        Instant now = Instant.now();
        int pausedCount = 0;
        for (ClassSession session : sessions) {
            if (classLivePresenceService.isPresent(session.getId(), session.getTeacherId())) {
                continue;
            }
            ClassSession lockedSession = classSessionRepository.findByIdForUpdate(session.getId()).orElse(null);
            if (lockedSession == null || liveStatus(lockedSession) != ClassLiveStatus.LIVE) {
                continue;
            }
            if (classLivePresenceService.isPresent(lockedSession.getId(), lockedSession.getTeacherId())) {
                continue;
            }
            lockedSession.setLiveStatus(ClassLiveStatus.PAUSED.getCode());
            lockedSession.setLivePausedAt(now);
            classSessionRepository.update(lockedSession);
            seatSyncWebSocketHub.broadcastLiveStatus(lockedSession.getId(), toSessionVO(lockedSession, false), "live_paused");
            pausedCount++;
        }
        return pausedCount;
    }

    private ClassroomInteractionAccess requireClassroomInteractionAccess(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensureClassOngoing(session);
        if (isCourseTeacher(session.getCourseId(), userId, role)) {
            ClassParticipant participant = ensureParticipant(session, userId, ClassParticipantRole.TEACHER, null, ZERO, ZERO, ZERO);
            return new ClassroomInteractionAccess(session, participant.getRole());
        }
        ClassParticipant participant = requireStudentSeat(session, userId, role);
        return new ClassroomInteractionAccess(session, participant.getRole());
    }

    private ClassSession requireJoinedPublishedSession(UUID sessionId, UUID userId, Integer role) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        ensurePublished(session);
        if (isCourseTeacher(session.getCourseId(), userId, role)) {
            ensureParticipant(session, userId, ClassParticipantRole.TEACHER, null, ZERO, ZERO, ZERO);
        }
        if (!joined(sessionId, userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        return session;
    }

    private ClassParticipant saveStudentSeat(ClassSession session, ClassParticipant existing, UUID userId,
                                             Integer seatIndex, BigDecimal x, BigDecimal y, BigDecimal z) {
        if (existing != null) {
            existing.setRole(ClassParticipantRole.STUDENT.getCode());
            existing.setSeatIndex(seatIndex);
            existing.setX(x);
            existing.setY(y);
            existing.setZ(z);
            classParticipantRepository.update(existing);
            return existing;
        }

        ClassParticipant participant = new ClassParticipant();
        participant.setId(UuidV7Generator.generate());
        participant.setSessionId(session.getId());
        participant.setUserId(userId);
        participant.setRole(ClassParticipantRole.STUDENT.getCode());
        participant.setSeatIndex(seatIndex);
        participant.setX(x);
        participant.setY(y);
        participant.setZ(z);
        participant.setJoinedAt(Instant.now());
        classParticipantRepository.save(participant);
        return participant;
    }

    private ClassParticipant ensureParticipant(ClassSession session, UUID userId, ClassParticipantRole role,
                                               Integer seatIndex,
                                               BigDecimal x, BigDecimal y, BigDecimal z) {
        return classParticipantRepository.findBySessionIdAndUserId(session.getId(), userId)
                .map(existing -> {
                    existing.setRole(role.getCode());
                    existing.setSeatIndex(seatIndex);
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
                    participant.setSeatIndex(seatIndex);
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

    private void requirePublishedSessionAccess(ClassSession session, UUID userId, Integer role) {
        ensurePublished(session);
        boolean isTeacher = isCourseTeacher(session.getCourseId(), userId, role);
        boolean isStudent = isStudentEnrolled(session.getCourseId(), userId);
        if (!isTeacher && !isStudent && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
    }

    private int validateSeatIndex(Integer roomSize, Integer seatIndex) {
        if (seatIndex == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Seat index is required");
        }
        int capacity = seatCapacity(roomSize);
        if (seatIndex < 0 || seatIndex >= capacity) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Seat index is out of range");
        }
        return seatIndex;
    }

    private int seatCapacity(Integer roomSize) {
        ClassRoomSize size = ClassRoomSize.fromCode(roomSize == null ? ClassRoomSize.SMALL.getCode() : roomSize);
        return switch (size) {
            case SMALL -> SMALL_SEAT_COUNT;
            case MEDIUM -> MEDIUM_SEAT_COUNT;
            case LARGE -> LARGE_SEAT_COUNT;
            case XLARGE -> XLARGE_SEAT_COUNT;
        };
    }

    private void ensureSeatAvailable(UUID sessionId, int seatIndex, UUID userId) {
        classParticipantRepository.findBySessionIdAndSeatIndex(sessionId, seatIndex)
                .filter(participant -> !participant.getUserId().equals(userId))
                .ifPresent(participant -> {
                    throw new BusinessException(ErrorCodes.BAD_REQUEST, "Seat is already occupied");
                });
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

    private void ensureClassOngoing(ClassSession session) {
        ensurePublished(session);
        if (classStatus(session) != ClassSessionStatus.LIVE) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class session is not live");
        }
    }

    private ClassParticipant requireStudentSeat(ClassSession session, UUID userId, Integer role) {
        if (!SecurityUtils.isStudent(role) || !isStudentEnrolled(session.getCourseId(), userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        return classParticipantRepository.findBySessionIdAndUserId(session.getId(), userId)
                .filter(participant -> participant.getRole() == ClassParticipantRole.STUDENT.getCode())
                .filter(participant -> participant.getSeatIndex() != null)
                .orElseThrow(() -> new BusinessException(ErrorCodes.FORBIDDEN));
    }

    private boolean joined(UUID sessionId, UUID userId) {
        return userId != null && classParticipantRepository.existsBySessionIdAndUserId(sessionId, userId);
    }

    private void validateTimeRange(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class session end time must be after start time");
        }
        if (Duration.between(startAt, endAt).compareTo(MAX_SESSION_DURATION) > 0) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class session duration cannot exceed 2 hours");
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private String roomName(UUID sessionId) {
        return "class-session-" + sessionId;
    }

    private ClassSessionStatus classStatus(ClassSession session) {
        return ClassSessionStatus.calculate(
                session.getPublishedAt(),
                session.getScheduledStartAt(),
                session.getScheduledEndAt(),
                Instant.now()
        );
    }

    private ClassLiveStatus liveStatus(ClassSession session) {
        return ClassLiveStatus.fromCode(session.getLiveStatus());
    }

    private ClassSessionVO toSessionVO(ClassSession session, boolean joined) {
        ClassSessionStatus status = classStatus(session);
        ClassLiveStatus liveStatus = liveStatus(session);
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
                joined,
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    private ClassParticipantVO toParticipantVO(ClassParticipant participant, Map<UUID, UserBasicInfo> userInfoMap) {
        UserBasicInfo userInfo = userInfoMap.get(participant.getUserId());
        return new ClassParticipantVO(
                participant.getId(),
                participant.getSessionId(),
                participant.getUserId(),
                participant.getRole(),
                participant.getSeatIndex(),
                participant.getX(),
                participant.getY(),
                participant.getZ(),
                userInfo != null ? userInfo.displayName() : null,
                userInfo != null ? userInfo.avatarUrl() : null,
                participant.getJoinedAt()
        );
    }

    private Map<UUID, UserBasicInfo> loadUserInfoMap(List<UUID> userIds) {
        return classroomUserInfoResolver.resolve(userIds);
    }

    private Map<UUID, Integer> loadParticipantRoleMap(UUID sessionId, List<UUID> userIds) {
        List<ClassParticipant> participants = classParticipantRepository.findBySessionIdAndUserIds(sessionId, userIds);
        if (participants == null || participants.isEmpty()) {
            return Map.of();
        }
        return participants.stream()
                .collect(Collectors.toMap(ClassParticipant::getUserId, ClassParticipant::getRole, (a, b) -> a));
    }

    private ClassBarrageVO toBarrageVO(ClassBarrage barrage, ClassSession session,
                                       Map<UUID, UserBasicInfo> userInfoMap,
                                       Map<UUID, Integer> participantRoleMap) {
        UserBasicInfo userInfo = userInfoMap.get(barrage.getSenderId());
        return new ClassBarrageVO(
                barrage.getId(),
                barrage.getSessionId(),
                barrage.getSenderId(),
                userInfo != null ? userInfo.displayName() : null,
                userInfo != null ? userInfo.avatarUrl() : null,
                senderRoleLabel(session, barrage.getSenderId(), participantRoleMap.get(barrage.getSenderId()), userInfo),
                barrage.getContent(),
                barrage.getSentAt()
        );
    }

    private String senderRoleLabel(ClassSession session, UUID senderId, Integer participantRole, UserBasicInfo userInfo) {
        if (session.getTeacherId().equals(senderId)) {
            return "开课教师";
        }
        if (participantRole != null && participantRole == ClassParticipantRole.TEACHER.getCode()) {
            return "听课教师";
        }
        if (userInfo != null && SecurityUtils.isTeacher(userInfo.role())) {
            return "听课教师";
        }
        return "学生";
    }

    private record ClassroomInteractionAccess(ClassSession session, Integer participantRole) {
    }
}
