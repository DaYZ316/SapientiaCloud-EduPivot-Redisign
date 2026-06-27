package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.course.event.LivePracticeAiGradingEventPublisher;
import com.dayz.sc.course.model.dto.CreateLivePracticeRequest;
import com.dayz.sc.course.model.dto.SubmitLivePracticeAnswerRequest;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.model.enums.ClassParticipantRole;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.enums.LivePracticeAiGradingStatus;
import com.dayz.sc.course.model.value.LivePracticeAnswerSnapshot;
import com.dayz.sc.course.repository.*;
import com.dayz.sc.course.sse.LivePracticeSseEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LivePracticeServiceTest {

    @Mock
    private LivePracticeGroupRepository livePracticeGroupRepository;

    @Mock
    private LivePracticeQuestionRepository livePracticeQuestionRepository;

    @Mock
    private LivePracticeSubmissionRepository livePracticeSubmissionRepository;

    @Mock
    private ClassSessionRepository classSessionRepository;

    @Mock
    private ClassParticipantRepository classParticipantRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private QuestionBankRepository questionBankRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionOptionRepository questionOptionRepository;

    @Mock
    private QuestionAnswerRepository questionAnswerRepository;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private LivePracticeAiGradingEventPublisher livePracticeAiGradingEventPublisher;

    @Mock
    private LivePracticeSseEmitter livePracticeSseEmitter;

    @Captor
    private ArgumentCaptor<List<LivePracticeQuestion>> questionSnapshotsCaptor;

    @Captor
    private ArgumentCaptor<LivePracticeSubmission> submissionCaptor;

    @Captor
    private ArgumentCaptor<LivePracticeAiGradingRequestedEvent> gradingEventCaptor;

    private LivePracticeService livePracticeService;

    @BeforeEach
    void setUp() {
        livePracticeService = new LivePracticeService(
                livePracticeGroupRepository,
                livePracticeQuestionRepository,
                livePracticeSubmissionRepository,
                classSessionRepository,
                classParticipantRepository,
                courseRepository,
                courseTeacherRepository,
                enrollmentRepository,
                questionBankRepository,
                questionRepository,
                questionOptionRepository,
                questionAnswerRepository,
                authInternalClient,
                livePracticeAiGradingEventPublisher,
                livePracticeSseEmitter
        );
    }

    @Test
    void createLivePractice_shouldRejectAiGradingWithoutShortAnswer() {
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        stubTeacherSession(courseId, sessionId, teacherId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question(questionId, courseId, 0)));

        assertThatThrownBy(() -> livePracticeService.createLivePractice(
                sessionId,
                createRequest(List.of(questionId), 1, "按要点给分"),
                teacherId,
                2
        )).isInstanceOf(BusinessException.class)
                .hasMessage("AI grading requires at least one short answer question");

        verify(livePracticeQuestionRepository, never()).saveBatch(anyList());
    }

    @Test
    void createLivePractice_shouldRejectAiGradingWithoutRequirement() {
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        stubTeacherSession(courseId, sessionId, teacherId);
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question(questionId, courseId, 4)));

        assertThatThrownBy(() -> livePracticeService.createLivePractice(
                sessionId,
                createRequest(List.of(questionId), 1, " "),
                teacherId,
                2
        )).isInstanceOf(BusinessException.class)
                .hasMessage("AI grading requirement is required");
    }

    @Test
    void createLivePractice_shouldEnableAiOnlyForShortAnswerSnapshots() {
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID choiceId = UUID.randomUUID();
        UUID blankId = UUID.randomUUID();
        UUID shortId = UUID.randomUUID();
        stubTeacherSession(courseId, sessionId, teacherId);
        when(questionRepository.findById(choiceId)).thenReturn(Optional.of(question(choiceId, courseId, 0)));
        when(questionRepository.findById(blankId)).thenReturn(Optional.of(question(blankId, courseId, 3)));
        when(questionRepository.findById(shortId)).thenReturn(Optional.of(question(shortId, courseId, 4)));

        livePracticeService.createLivePractice(
                sessionId,
                createRequest(List.of(choiceId, blankId, shortId), 1, "按要点给分"),
                teacherId,
                2
        );

        verify(livePracticeQuestionRepository).saveBatch(questionSnapshotsCaptor.capture());
        List<LivePracticeQuestion> snapshots = questionSnapshotsCaptor.getValue();
        assertThat(snapshots).extracting(LivePracticeQuestion::getAiGradingEnabled)
                .containsExactly(0, 0, 1);
    }

    @Test
    void submitAnswer_shouldPublishAiGradingRequestWithRequirement() {
        UUID courseId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionSnapshotId = UUID.randomUUID();
        var group = livePracticeGroup(groupId, courseId, sessionId, "grade by key concepts");
        var question = livePracticeQuestion(questionSnapshotId, groupId, courseId, sessionId);
        when(livePracticeGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(Optional.of(enrollment(studentId)));
        when(livePracticeQuestionRepository.findById(questionSnapshotId)).thenReturn(Optional.of(question));
        when(livePracticeSubmissionRepository.findByGroupIdAndQuestionSnapshotIdAndStudentId(groupId, questionSnapshotId, studentId))
                .thenReturn(Optional.empty());
        when(livePracticeAiGradingEventPublisher.publishRequested(any())).thenReturn(true);

        livePracticeService.submitAnswer(
                groupId,
                questionSnapshotId,
                new SubmitLivePracticeAnswerRequest(null, "封装隐藏内部实现。"),
                studentId,
                1
        );

        verify(livePracticeSubmissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getAiGradingStatus()).isEqualTo(LivePracticeAiGradingStatus.PENDING.name());
        verify(livePracticeAiGradingEventPublisher).publishRequested(gradingEventCaptor.capture());
        assertThat(gradingEventCaptor.getValue().eventId()).isNotNull();
        assertThat(gradingEventCaptor.getValue().submissionId()).isEqualTo(submissionCaptor.getValue().getId());
        assertThat(gradingEventCaptor.getValue().gradingRequirement()).isEqualTo("grade by key concepts");
        assertThat(gradingEventCaptor.getValue().textAnswer()).isEqualTo("封装隐藏内部实现。");
    }

    @Test
    void submitAnswer_shouldPublishAiGradingRequestAfterTransactionCommit() {
        UUID courseId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionSnapshotId = UUID.randomUUID();
        var group = livePracticeGroup(groupId, courseId, sessionId, "按关键概念给分");
        var question = livePracticeQuestion(questionSnapshotId, groupId, courseId, sessionId);
        when(livePracticeGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(Optional.of(enrollment(studentId)));
        when(livePracticeQuestionRepository.findById(questionSnapshotId)).thenReturn(Optional.of(question));
        when(livePracticeSubmissionRepository.findByGroupIdAndQuestionSnapshotIdAndStudentId(groupId, questionSnapshotId, studentId))
                .thenReturn(Optional.empty());
        when(livePracticeAiGradingEventPublisher.publishRequested(any())).thenReturn(true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            livePracticeService.submitAnswer(
                    groupId,
                    questionSnapshotId,
                    new SubmitLivePracticeAnswerRequest(null, "Encapsulation hides implementation."),
                    studentId,
                    1
            );

            verify(livePracticeAiGradingEventPublisher, never()).publishRequested(any());
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);

            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(livePracticeAiGradingEventPublisher).publishRequested(gradingEventCaptor.capture());
            assertThat(gradingEventCaptor.getValue().submissionId()).isNotNull();
            assertThat(gradingEventCaptor.getValue().eventId()).isNotNull();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void submitAnswer_shouldMarkFailedWhenAiGradingRequestCannotBePublished() {
        UUID courseId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionSnapshotId = UUID.randomUUID();
        var group = livePracticeGroup(groupId, courseId, sessionId, "按关键概念给分");
        var question = livePracticeQuestion(questionSnapshotId, groupId, courseId, sessionId);
        when(livePracticeGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(Optional.of(enrollment(studentId)));
        when(livePracticeQuestionRepository.findById(questionSnapshotId)).thenReturn(Optional.of(question));
        when(livePracticeSubmissionRepository.findByGroupIdAndQuestionSnapshotIdAndStudentId(groupId, questionSnapshotId, studentId))
                .thenReturn(Optional.empty());
        when(livePracticeAiGradingEventPublisher.publishRequested(any())).thenReturn(false);

        livePracticeService.submitAnswer(
                groupId,
                questionSnapshotId,
                new SubmitLivePracticeAnswerRequest(null, "封装隐藏内部实现。"),
                studentId,
                1
        );

        verify(livePracticeSubmissionRepository).update(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getAiGradingStatus()).isEqualTo(LivePracticeAiGradingStatus.FAILED.name());
        assertThat(submissionCaptor.getValue().getAiGradingError()).contains("Kafka");
    }

    @Test
    void getLivePractice_shouldKeepPendingAiGradingWhenReading() {
        UUID courseId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionSnapshotId = UUID.randomUUID();
        var group = livePracticeGroup(groupId, courseId, sessionId, "按关键概念给分");
        var question = livePracticeQuestion(questionSnapshotId, groupId, courseId, sessionId);
        var submission = new LivePracticeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setGroupId(groupId);
        submission.setQuestionSnapshotId(questionSnapshotId);
        submission.setCourseId(courseId);
        submission.setClassSessionId(sessionId);
        submission.setStudentId(studentId);
        submission.setSubmitStatus(1);
        submission.setEarnedScore(BigDecimal.ZERO);
        submission.setAiGradingStatus(LivePracticeAiGradingStatus.PENDING.name());
        submission.setSubmittedAt(Instant.now().minusSeconds(180));
        when(livePracticeGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(Optional.of(enrollment(studentId)));
        when(livePracticeQuestionRepository.findByGroupId(groupId)).thenReturn(List.of(question));
        when(livePracticeSubmissionRepository.findByGroupId(groupId)).thenReturn(List.of(submission));
        when(enrollmentRepository.findActiveOrCompletedByCourseId(courseId)).thenReturn(List.of(enrollment(studentId)));

        livePracticeService.getLivePractice(groupId, studentId, 1);

        verify(livePracticeSubmissionRepository, never()).update(any());
    }

    @Test
    void getStudentWorkbook_shouldKeepPendingAiGradingWhenReading() {
        UUID courseId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionSnapshotId = UUID.randomUUID();
        var group = livePracticeGroup(groupId, courseId, sessionId, "grade by key concepts");
        var question = livePracticeQuestion(questionSnapshotId, groupId, courseId, sessionId);
        var submission = new LivePracticeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setGroupId(groupId);
        submission.setQuestionSnapshotId(questionSnapshotId);
        submission.setCourseId(courseId);
        submission.setClassSessionId(sessionId);
        submission.setStudentId(studentId);
        submission.setSubmitStatus(1);
        submission.setEarnedScore(BigDecimal.ZERO);
        submission.setAiGradingStatus(LivePracticeAiGradingStatus.PENDING.name());
        submission.setSubmittedAt(Instant.now().minusSeconds(180));
        when(enrollmentRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(Optional.of(enrollment(studentId)));
        when(livePracticeGroupRepository.findByCourseId(courseId)).thenReturn(List.of(group));
        when(livePracticeQuestionRepository.findByCourseId(courseId)).thenReturn(List.of(question));
        when(livePracticeSubmissionRepository.findByCourseIdAndStudentId(courseId, studentId))
                .thenReturn(List.of(submission));

        var workbook = livePracticeService.getStudentWorkbook(courseId, studentId, 1);

        verify(livePracticeSubmissionRepository, never()).update(any());
        assertThat(workbook).hasSize(1);
        assertThat(workbook.getFirst().submission().aiGradingStatus()).isEqualTo(LivePracticeAiGradingStatus.PENDING.name());
    }

    @Test
    void resubmitPendingAiGradingRequests_shouldRepublishStalePendingSubmission() {
        UUID courseId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID questionSnapshotId = UUID.randomUUID();
        var group = livePracticeGroup(groupId, courseId, sessionId, "grade by key concepts");
        var question = livePracticeQuestion(questionSnapshotId, groupId, courseId, sessionId);
        var submission = new LivePracticeSubmission();
        submission.setId(UUID.randomUUID());
        submission.setGroupId(groupId);
        submission.setQuestionSnapshotId(questionSnapshotId);
        submission.setCourseId(courseId);
        submission.setClassSessionId(sessionId);
        submission.setStudentId(studentId);
        submission.setTextAnswer("Encapsulation hides implementation.");
        submission.setAiGradingStatus(LivePracticeAiGradingStatus.PENDING.name());
        submission.setSubmittedAt(Instant.now().minusSeconds(180));
        when(livePracticeSubmissionRepository.findPendingAiGradingSubmittedBefore(any(), any(Integer.class)))
                .thenReturn(List.of(submission));
        when(livePracticeQuestionRepository.findById(questionSnapshotId)).thenReturn(Optional.of(question));
        when(livePracticeGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(livePracticeAiGradingEventPublisher.publishRequested(any())).thenReturn(true);

        livePracticeService.resubmitPendingAiGradingRequests();
        livePracticeService.resubmitPendingAiGradingRequests();

        verify(livePracticeAiGradingEventPublisher, times(2)).publishRequested(gradingEventCaptor.capture());
        assertThat(gradingEventCaptor.getAllValues())
                .extracting(LivePracticeAiGradingRequestedEvent::submissionId)
                .containsOnly(submission.getId());
        assertThat(gradingEventCaptor.getAllValues())
                .extracting(LivePracticeAiGradingRequestedEvent::eventId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    }

    private void stubTeacherSession(UUID courseId, UUID sessionId, UUID teacherId) {
        ClassSession session = new ClassSession();
        session.setId(sessionId);
        session.setCourseId(courseId);
        session.setPublishedAt(Instant.now());
        when(classSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, teacherId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(new Course()));
        when(livePracticeGroupRepository.countByClassSessionId(sessionId)).thenReturn(0L);
        lenient().when(classParticipantRepository.findBySessionId(sessionId)).thenReturn(List.of(classParticipant(UUID.randomUUID())));
        lenient().when(questionOptionRepository.findByQuestionId(any())).thenReturn(List.of());
        lenient().when(questionAnswerRepository.findByQuestionId(any())).thenReturn(List.of());
    }

    private CreateLivePracticeRequest createRequest(List<UUID> selectedQuestionIds, int aiEnabled, String requirement) {
        return new CreateLivePracticeRequest(
                "课堂练习",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(600),
                0,
                aiEnabled,
                requirement,
                selectedQuestionIds,
                List.of()
        );
    }

    private Question question(UUID id, UUID courseId, int type) {
        Question question = new Question();
        question.setId(id);
        question.setCourseId(courseId);
        question.setQuestionTitle("题目" + type);
        question.setQuestionContent("题干");
        question.setQuestionType(type);
        question.setDifficulty(2);
        question.setScore(BigDecimal.TEN);
        question.setAllowPartialCredit(0);
        return question;
    }

    private ClassParticipant classParticipant(UUID studentId) {
        ClassParticipant participant = new ClassParticipant();
        participant.setUserId(studentId);
        participant.setRole(ClassParticipantRole.STUDENT.getCode());
        return participant;
    }

    private Enrollment enrollment(UUID studentId) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setStatus(EnrollmentStatus.ACTIVE.getCode());
        return enrollment;
    }

    private com.dayz.sc.course.model.entity.LivePracticeGroup livePracticeGroup(
            UUID groupId,
            UUID courseId,
            UUID sessionId,
            String requirement) {
        var group = new com.dayz.sc.course.model.entity.LivePracticeGroup();
        group.setId(groupId);
        group.setCourseId(courseId);
        group.setClassSessionId(sessionId);
        group.setAvailableStartAt(Instant.now().minusSeconds(60));
        group.setAvailableEndAt(Instant.now().plusSeconds(600));
        group.setAllowLateSubmission(0);
        group.setAiGradingEnabled(1);
        group.setAiGradingRequirement(requirement);
        return group;
    }

    private LivePracticeQuestion livePracticeQuestion(UUID id, UUID groupId, UUID courseId, UUID sessionId) {
        LivePracticeQuestion question = new LivePracticeQuestion();
        question.setId(id);
        question.setGroupId(groupId);
        question.setCourseId(courseId);
        question.setClassSessionId(sessionId);
        question.setQuestionTitle("解释封装");
        question.setQuestionContent("请解释封装。");
        question.setQuestionType(4);
        question.setScore(BigDecimal.TEN);
        question.setAiGradingEnabled(1);
        question.setAnswersSnapshot(List.of(new LivePracticeAnswerSnapshot(
                UUID.randomUUID(),
                "封装隐藏内部实现并暴露必要接口。",
                null,
                BigDecimal.TEN,
                1
        )));
        return question;
    }
}
