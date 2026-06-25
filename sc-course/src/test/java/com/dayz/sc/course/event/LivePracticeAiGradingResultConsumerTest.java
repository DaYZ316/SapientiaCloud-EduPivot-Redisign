package com.dayz.sc.course.event;

import com.dayz.sc.common.events.ai.LivePracticeAiGradingCompletedEvent;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import com.dayz.sc.course.model.entity.LivePracticeGroup;
import com.dayz.sc.course.model.entity.LivePracticeQuestion;
import com.dayz.sc.course.model.entity.LivePracticeSubmission;
import com.dayz.sc.course.model.enums.LivePracticeAiGradingStatus;
import com.dayz.sc.course.repository.LivePracticeGroupRepository;
import com.dayz.sc.course.repository.LivePracticeQuestionRepository;
import com.dayz.sc.course.repository.LivePracticeSubmissionRepository;
import com.dayz.sc.course.sse.LivePracticeSseEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LivePracticeAiGradingResultConsumerTest {

    @Mock
    private LivePracticeSubmissionRepository livePracticeSubmissionRepository;

    @Mock
    private LivePracticeQuestionRepository livePracticeQuestionRepository;

    @Mock
    private LivePracticeGroupRepository livePracticeGroupRepository;

    @Mock
    private LivePracticeSseEmitter livePracticeSseEmitter;

    @Mock
    private KafkaIdempotencyGuard kafkaIdempotencyGuard;

    @Mock
    private Acknowledgment acknowledgment;

    @Captor
    private ArgumentCaptor<LivePracticeSubmission> submissionCaptor;

    private LivePracticeAiGradingResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new LivePracticeAiGradingResultConsumer(
                livePracticeSubmissionRepository,
                livePracticeQuestionRepository,
                livePracticeGroupRepository,
                livePracticeSseEmitter,
                kafkaIdempotencyGuard
        );
    }

    @Test
    void onGradingCompleted_shouldClampScoreAndUpdateSubmission() {
        UUID submissionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        LivePracticeSubmission submission = submission(submissionId, questionId);
        LivePracticeQuestion question = new LivePracticeQuestion();
        question.setId(questionId);
        question.setScore(BigDecimal.TEN);
        when(kafkaIdempotencyGuard.tryAcquire(any(), any())).thenReturn(true);
        when(livePracticeSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(livePracticeQuestionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(livePracticeGroupRepository.findById(submission.getGroupId())).thenReturn(Optional.of(group(submission)));

        consumer.onGradingCompleted(event(submission, "COMPLETED", BigDecimal.valueOf(99), null), acknowledgment);

        verify(livePracticeSubmissionRepository).update(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getEarnedScore()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(submissionCaptor.getValue().getAiGradingStatus()).isEqualTo(LivePracticeAiGradingStatus.COMPLETED.name());
        assertThat(submissionCaptor.getValue().getAiGradingFeedback()).isEqualTo("不错");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onGradingCompleted_shouldPersistFailure() {
        UUID submissionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        LivePracticeSubmission submission = submission(submissionId, questionId);
        when(kafkaIdempotencyGuard.tryAcquire(any(), any())).thenReturn(true);
        when(livePracticeSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
        when(livePracticeGroupRepository.findById(submission.getGroupId())).thenReturn(Optional.of(group(submission)));

        consumer.onGradingCompleted(event(submission, "FAILED", BigDecimal.ZERO, "模型错误"), acknowledgment);

        verify(livePracticeSubmissionRepository).update(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getAiGradingStatus()).isEqualTo(LivePracticeAiGradingStatus.FAILED.name());
        assertThat(submissionCaptor.getValue().getAiGradingError()).isEqualTo("模型错误");
    }

    private LivePracticeSubmission submission(UUID submissionId, UUID questionId) {
        LivePracticeSubmission submission = new LivePracticeSubmission();
        submission.setId(submissionId);
        submission.setGroupId(UUID.randomUUID());
        submission.setQuestionSnapshotId(questionId);
        submission.setCourseId(UUID.randomUUID());
        submission.setClassSessionId(UUID.randomUUID());
        submission.setStudentId(UUID.randomUUID());
        submission.setAiGradingStatus(LivePracticeAiGradingStatus.PENDING.name());
        return submission;
    }

    private LivePracticeGroup group(LivePracticeSubmission submission) {
        LivePracticeGroup group = new LivePracticeGroup();
        group.setId(submission.getGroupId());
        group.setCourseId(submission.getCourseId());
        group.setClassSessionId(submission.getClassSessionId());
        return group;
    }

    private LivePracticeAiGradingCompletedEvent event(
            LivePracticeSubmission submission,
            String status,
            BigDecimal score,
            String errorMessage) {
        return new LivePracticeAiGradingCompletedEvent(
                UUID.randomUUID(),
                submission.getId(),
                submission.getGroupId(),
                submission.getQuestionSnapshotId(),
                submission.getCourseId(),
                submission.getClassSessionId(),
                submission.getStudentId(),
                status,
                1,
                score,
                "不错",
                errorMessage,
                "LIVE_PRACTICE_AI_GRADING_COMPLETED",
                Instant.now(),
                "sc-ai"
        );
    }
}
