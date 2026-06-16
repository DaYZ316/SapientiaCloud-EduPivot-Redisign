package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.course.model.dto.CreateClassBarrageRequest;
import com.dayz.sc.course.model.dto.CreateClassSessionRequest;
import com.dayz.sc.course.model.dto.JoinClassSessionRequest;
import com.dayz.sc.course.model.dto.UpdateClassSessionRequest;
import com.dayz.sc.course.model.entity.ClassBarrage;
import com.dayz.sc.course.model.entity.ClassParticipant;
import com.dayz.sc.course.model.entity.ClassSession;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.enums.ClassSessionStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.LiveKitTokenVO;
import com.dayz.sc.course.repository.ClassBarrageRepository;
import com.dayz.sc.course.repository.ClassParticipantRepository;
import com.dayz.sc.course.repository.ClassSessionRepository;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import com.dayz.sc.course.sse.ClassBarrageSseEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                liveKitTokenService
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
    void joinSession_shouldRejectDraft() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session(sessionId, null)));

        assertThatThrownBy(() -> classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(BigDecimal.ONE, BigDecimal.TEN, null), studentId, 1))
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
        when(classParticipantRepository.findBySessionIdAndUserId(sessionId, studentId)).thenReturn(Optional.empty());

        classSessionService.joinSession(sessionId,
                new JoinClassSessionRequest(new BigDecimal("12.50"), new BigDecimal("9.25"), null), studentId, 1);

        verify(classParticipantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getX()).isEqualByComparingTo("12.50");
        assertThat(participantCaptor.getValue().getY()).isEqualByComparingTo("9.25");
        assertThat(participantCaptor.getValue().getZ()).isEqualByComparingTo("0");
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
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(classParticipantRepository.existsBySessionIdAndUserId(sessionId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> classSessionService.createLiveToken(sessionId, studentId, 1))
                .isInstanceOf(BusinessException.class);
        verify(liveKitTokenService, never()).createToken(any(), any(), any(Boolean.class));
    }

    @Test
    void createLiveToken_shouldReturnTokenForJoinedUser() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        LiveKitTokenVO token = new LiveKitTokenVO("wss://live.test", session.getLiveRoomName(), "token", Instant.now().plusSeconds(60));
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(classParticipantRepository.existsBySessionIdAndUserId(sessionId, studentId)).thenReturn(true);
        when(liveKitTokenService.createToken(studentId, session.getLiveRoomName(), false)).thenReturn(token);

        assertThat(classSessionService.createLiveToken(sessionId, studentId, 1)).isSameAs(token);
    }

    @Test
    void sendBarrage_shouldRejectUserNotJoined() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(classParticipantRepository.existsBySessionIdAndUserId(sessionId, studentId)).thenReturn(false);

        assertThatThrownBy(() -> classSessionService.sendBarrage(sessionId,
                new CreateClassBarrageRequest("hello"), studentId, 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sendBarrage_shouldPersistAndBroadcast_whenJoined() {
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassSession session = session(sessionId, Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(classParticipantRepository.existsBySessionIdAndUserId(sessionId, studentId)).thenReturn(true);

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
        session.setScheduledStartAt(Instant.now().plusSeconds(3600));
        session.setScheduledEndAt(Instant.now().plusSeconds(7200));
        session.setPublishedAt(publishedAt);
        session.setRoomSize(0);
        session.setLiveRoomName("class-session-" + sessionId);
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
}
