package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.course.model.dto.CreateClassBarrageRequest;
import com.dayz.sc.course.model.dto.CreateClassSessionRequest;
import com.dayz.sc.course.model.dto.JoinClassSessionRequest;
import com.dayz.sc.course.model.dto.UpdateClassSessionRequest;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.model.enums.ClassLiveStatus;
import com.dayz.sc.course.model.enums.ClassSessionStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.LiveKitTokenVO;
import com.dayz.sc.course.repository.*;
import com.dayz.sc.course.sse.ClassBarrageSseEmitter;
import com.dayz.sc.course.websocket.ClassSeatSyncTokenService;
import com.dayz.sc.course.websocket.ClassSeatSyncWebSocketHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClassSessionServiceTest {

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private ClassParticipantRepository classParticipantRepository;

    @Mock
    private ClassBarrageRepository classBarrageRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ClassBarrageSseEmitter barrageSseEmitter;

    @Mock
    private LiveKitTokenService liveKitTokenService;

    @Mock
    private LiveKitRoomService liveKitRoomService;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private ClassSeatSyncTokenService classSeatSyncTokenService;

    @Mock
    private ClassSeatSyncWebSocketHub seatSyncWebSocketHub;

    @Captor
    private ArgumentCaptor<ClassSession> sessionCaptor;

    @Captor
    private ArgumentCaptor<ClassParticipant> participantCaptor;

    @Captor
    private ArgumentCaptor<ClassBarrage> barrageCaptor;

    private ClassSessionService classSessionService;

    @BeforeEach
    void setUp() {
        classSessionService = new ClassSessionService(
                classSessionRepository,
                classParticipantRepository,
                classBarrageRepository,
                courseRepository,
                courseTeacherRepository,
                enrollmentRepository,
                barrageSseEmitter,
                liveKitTokenService,
                liveKitRoomService,
                authInternalClient,
                classSeatSyncTokenService,
                seatSyncWebSocketHub
        );
    }

    @Test
    void createSession_shouldCreateDraftForCourseTeacher() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, teacherId)));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)).thenReturn(true);

        classSessionService.createSession(new CreateClassSessionRequest(
                courseId,
                "Intro live",
                "hello",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                1
        ), teacherId, 2);

        verify(classSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getPublishedAt()).isNull();
        assertThat(sessionCaptor.getValue().getLiveRoomName()).startsWith("class-session-");
    }

    @Test
    void createSession_shouldRejectStudent() {
        UUID studentId = UUID.randomUUID();

        assertThatThrownBy(() -> classSessionService.createSession(new CreateClassSessionRequest(
                UUID.randomUUID(),
                "Intro live",
                "hello",
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                1
        ), studentId, 1))
                .isInstanceOf(BusinessException.class);
        verify(classSessionRepository, never()).save(any());
    }

    @Test
    void createSession_shouldAllowExactlyTwoHours() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(3600);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, teacherId)));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)).thenReturn(true);

        classSessionService.createSession(new CreateClassSessionRequest(
                courseId,
                "Intro live",
                "hello",
                startAt,
                startAt.plusSeconds(7200),
                1
        ), teacherId, 2);

        verify(classSessionRepository).save(any());
    }

    @Test
    void createSession_shouldRejectDurationOverTwoHours() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Instant startAt = Instant.now().plusSeconds(3600);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, teacherId)));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)).thenReturn(true);

        assertThatThrownBy(() -> classSessionService.createSession(new CreateClassSessionRequest(
                courseId,
                "Intro live",
                "hello",
                startAt,
                startAt.plusSeconds(7201),
                1
        ), teacherId, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Class session duration cannot exceed 2 hours");
        verify(classSessionRepository, never()).save(any());
    }

    @Test
    void listByCourse_shouldAllowEnrolledStudentWithoutDrafts() {
        UUID courseId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(UUID.randomUUID(), Instant.now());
        session.setCourseId(courseId);
        Page<ClassSession> page = new Page<>(1, 10);
        page.setRecords(List.of(session));
        page.setTotal(1);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, UUID.randomUUID())));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classSessionRepository.findByCourseId(courseId, 1, 10, false)).thenReturn(page);

        var result = classSessionService.listByCourse(courseId, 1, 10, studentId, 1);

        assertThat(result.records()).hasSize(1);
        verify(classSessionRepository).findByCourseId(courseId, 1, 10, false);
    }

    @Test
    void joinSession_shouldRejectDraft() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session(sessionId, null)));

        assertThatThrownBy(() -> classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(BigDecimal.ONE, BigDecimal.TEN, null, 0), studentId, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Class session is still preparing");
    }

    @Test
    void updateSession_shouldRejectPublishedSession() {
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        session.setTeacherId(teacherId);
        when(classSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(session.getCourseId(), teacherId)).thenReturn(true);

        assertThatThrownBy(() -> classSessionService.updateSession(sessionId,
                new UpdateClassSessionRequest("New title", null, null, null, null), teacherId, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Published class sessions cannot be changed");
    }

    @Test
    void updateSession_shouldRejectStudent() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, null);
        when(classSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> classSessionService.updateSession(sessionId,
                new UpdateClassSessionRequest("New title", null, null, null, null), studentId, 1))
                .isInstanceOf(BusinessException.class);
        verify(classSessionRepository, never()).update(any());
    }

    @Test
    void publishSession_shouldRejectStudent() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, null);
        when(classSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> classSessionService.publishSession(sessionId, studentId, 1))
                .isInstanceOf(BusinessException.class);
        verify(classSessionRepository, never()).update(any());
    }

    @Test
    void deleteSession_shouldRejectStudent() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> classSessionService.deleteSession(sessionId, studentId, 1))
                .isInstanceOf(BusinessException.class);
        verify(classSessionRepository, never()).deleteById(any());
    }

    @Test
    void getSession_shouldCalculateRuntimeStatus() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now().minusSeconds(3600));
        session.setScheduledStartAt(Instant.now().minusSeconds(60));
        session.setScheduledEndAt(Instant.now().plusSeconds(3600));
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));

        assertThat(classSessionService.getSession(sessionId, studentId, 1).status())
                .isEqualTo(ClassSessionStatus.LIVE.getCode());
    }

    @Test
    void joinSession_shouldPersistStudentPosition_whenEnrolled() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndSeatIndex(sessionId, 5)).thenReturn(Optional.empty());
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.empty());

        classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(new BigDecimal("12.50"), new BigDecimal("9.25"), null, 5), studentId, 1);

        verify(classParticipantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getSeatIndex()).isEqualTo(5);
        assertThat(participantCaptor.getValue().getX()).isEqualByComparingTo("12.50");
        assertThat(participantCaptor.getValue().getY()).isEqualByComparingTo("9.25");
        assertThat(participantCaptor.getValue().getZ()).isEqualByComparingTo("0");
        verify(seatSyncWebSocketHub).broadcastUpsert(eq(sessionId), any());
    }

    @Test
    void joinSession_shouldMoveStudentToEmptySeat_whenAlreadySeated() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        ClassParticipant existing = participant(sessionId, studentId, 2);
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.of(existing));
        when(classParticipantRepository.findBySessionIdAndSeatIndex(sessionId, 6)).thenReturn(Optional.empty());

        classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(new BigDecimal("3.50"), new BigDecimal("4.25"), BigDecimal.ONE, 6), studentId, 1);

        verify(classParticipantRepository).update(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getSeatIndex()).isEqualTo(6);
        assertThat(participantCaptor.getValue().getX()).isEqualByComparingTo("3.50");
        assertThat(participantCaptor.getValue().getY()).isEqualByComparingTo("4.25");
        assertThat(participantCaptor.getValue().getZ()).isEqualByComparingTo("1");
        verify(seatSyncWebSocketHub).broadcastUpsert(eq(sessionId), any());
    }

    @Test
    void joinSession_shouldReturnExistingParticipant_whenStudentSelectsOwnSeat() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        ClassParticipant existing = participant(sessionId, studentId, 5);
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.of(existing));

        var participant = classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 5), studentId, 1);

        assertThat(participant.id()).isEqualTo(existing.getId());
        verify(classParticipantRepository, never()).update(any());
        verify(classParticipantRepository, never()).save(any());
        verifyNoInteractions(seatSyncWebSocketHub);
    }

    @Test
    void joinSession_shouldRejectTeacher() {
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, 1), teacherId, 2))
                .isInstanceOf(BusinessException.class);
        verify(classParticipantRepository, never()).save(any());
        verify(classParticipantRepository, never()).update(any());
    }

    @Test
    void joinSession_shouldRejectUnenrolledStudent() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, 1), studentId, 1))
                .isInstanceOf(BusinessException.class);
        verify(classParticipantRepository, never()).save(any());
        verify(classParticipantRepository, never()).update(any());
    }

    @Test
    void joinSession_shouldRejectSeatOccupiedByAnotherStudent() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID anotherStudentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        ClassParticipant occupied = new ClassParticipant();
        occupied.setSessionId(sessionId);
        occupied.setUserId(anotherStudentId);
        occupied.setSeatIndex(8);
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.empty());
        when(classParticipantRepository.findBySessionIdAndSeatIndex(sessionId, 8)).thenReturn(Optional.of(occupied));

        assertThatThrownBy(() -> classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, 8), studentId, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Seat is already occupied");
        verify(classParticipantRepository, never()).save(any());
    }

    @Test
    void getSession_shouldAutoJoinTeacher_whenPublished() {
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(session.getCourseId(), teacherId)).thenReturn(true);
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, teacherId)).thenReturn(Optional.empty());

        classSessionService.getSession(sessionId, teacherId, 2);

        verify(classParticipantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getRole()).isEqualTo(0);
    }

    @Test
    void createLiveToken_shouldRejectUserNotJoined() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        session.setLiveStatus(ClassLiveStatus.LIVE.getCode());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classSessionService.createLiveToken(sessionId, studentId, 1))
                .isInstanceOf(BusinessException.class);
        verify(liveKitTokenService, never()).createToken(any(), any(), any(Boolean.class));
    }

    @Test
    void createLiveToken_shouldReturnSubscribeTokenForSeatedStudent() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        session.setLiveStatus(ClassLiveStatus.LIVE.getCode());
        ClassParticipant participant = participant(sessionId, studentId, 4);
        LiveKitTokenVO token = new LiveKitTokenVO("wss://live.test", session.getLiveRoomName(), "token", Instant.now().plusSeconds(60));
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.of(participant));
        when(liveKitTokenService.createToken(studentId, session.getLiveRoomName(), false)).thenReturn(token);

        assertThat(classSessionService.createLiveToken(sessionId, studentId, 1)).isSameAs(token);
    }

    @Test
    void startLive_shouldSetLiveStatus_whenClassOngoing() {
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        session.setTeacherId(teacherId);
        when(classSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(session.getCourseId(), teacherId)).thenReturn(true);
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, teacherId)).thenReturn(Optional.empty());

        var result = classSessionService.startLive(sessionId, teacherId, 2);

        assertThat(result.liveStatus()).isEqualTo(ClassLiveStatus.LIVE.getCode());
        verify(classSessionRepository).update(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getLiveStartedAt()).isNotNull();
        verify(seatSyncWebSocketHub).broadcastLiveStatus(eq(sessionId), any(), eq("live_started"));
    }

    @Test
    void pauseResumeStopLive_shouldMoveBetweenLiveStates() {
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        session.setTeacherId(teacherId);
        session.setLiveStatus(ClassLiveStatus.LIVE.getCode());
        when(classSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(session.getCourseId(), teacherId)).thenReturn(true);

        assertThat(classSessionService.pauseLive(sessionId, teacherId, 2).liveStatus())
                .isEqualTo(ClassLiveStatus.PAUSED.getCode());
        assertThat(classSessionService.resumeLive(sessionId, teacherId, 2).liveStatus())
                .isEqualTo(ClassLiveStatus.LIVE.getCode());
        assertThat(classSessionService.stopLive(sessionId, teacherId, 2).liveStatus())
                .isEqualTo(ClassLiveStatus.ENDED.getCode());
        verify(liveKitRoomService).deleteRoom(session.getLiveRoomName());
    }

    @Test
    void sendBarrage_shouldRejectUserNotJoined() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classSessionService.sendBarrage(sessionId,
                new CreateClassBarrageRequest("hello"), studentId, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sendBarrage_shouldPersistAndBroadcast_whenJoined() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        ClassParticipant participant = participant(sessionId, studentId, 3);
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.findByCourseIdAndStudentId(session.getCourseId(), studentId))
                .thenReturn(Optional.of(enrollment(studentId, EnrollmentStatus.ACTIVE.getCode())));
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.of(participant));

        classSessionService.sendBarrage(sessionId, new CreateClassBarrageRequest(" hello "), studentId, 1);

        verify(classBarrageRepository).save(barrageCaptor.capture());
        assertThat(barrageCaptor.getValue().getContent()).isEqualTo("hello");
        verify(barrageSseEmitter).broadcast(any(), any());
    }

    private Course course(UUID courseId, UUID teacherId) {
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(teacherId);
        return course;
    }

    private ClassSession session(UUID sessionId, Instant publishedAt) {
        ClassSession session = new ClassSession();
        session.setId(sessionId);
        session.setCourseId(UUID.randomUUID());
        session.setTeacherId(UUID.randomUUID());
        session.setTitle("Intro");
        session.setScheduledStartAt(Instant.now().minusSeconds(60));
        session.setScheduledEndAt(Instant.now().plusSeconds(3600));
        session.setPublishedAt(publishedAt);
        session.setRoomSize(0);
        session.setLiveRoomName("class-session-" + sessionId);
        session.setLiveStatus(ClassLiveStatus.NOT_STARTED.getCode());
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        return session;
    }

    private Enrollment enrollment(UUID studentId, int status) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setCourseId(UUID.randomUUID());
        enrollment.setStudentId(studentId);
        enrollment.setStatus(status);
        return enrollment;
    }

    private ClassParticipant participant(UUID sessionId, UUID userId, Integer seatIndex) {
        ClassParticipant participant = new ClassParticipant();
        participant.setId(UUID.randomUUID());
        participant.setSessionId(sessionId);
        participant.setUserId(userId);
        participant.setRole(1);
        participant.setSeatIndex(seatIndex);
        participant.setX(BigDecimal.ONE);
        participant.setY(BigDecimal.ONE);
        participant.setZ(BigDecimal.ZERO);
        participant.setJoinedAt(Instant.now());
        return participant;
    }
}
