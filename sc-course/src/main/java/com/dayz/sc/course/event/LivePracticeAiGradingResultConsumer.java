package com.dayz.sc.course.event;

import com.dayz.sc.common.events.ai.LivePracticeAiGradingCompletedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import com.dayz.sc.course.model.entity.LivePracticeGroup;
import com.dayz.sc.course.model.entity.LivePracticeQuestion;
import com.dayz.sc.course.model.entity.LivePracticeSubmission;
import com.dayz.sc.course.model.enums.LivePracticeAiGradingStatus;
import com.dayz.sc.course.repository.LivePracticeGroupRepository;
import com.dayz.sc.course.repository.LivePracticeQuestionRepository;
import com.dayz.sc.course.repository.LivePracticeSubmissionRepository;
import com.dayz.sc.course.sse.LivePracticeSseEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * LivePracticeAiGradingResultConsumer.
 *
 * @author DaYZ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LivePracticeAiGradingResultConsumer {

    private static final String GROUP_ID = "sc-course";
    private static final int FLAG_ON = 1;
    private static final int FLAG_OFF = 0;

    private final LivePracticeSubmissionRepository livePracticeSubmissionRepository;
    private final LivePracticeQuestionRepository livePracticeQuestionRepository;
    private final LivePracticeGroupRepository livePracticeGroupRepository;
    private final LivePracticeSseEmitter livePracticeSseEmitter;
    private final KafkaIdempotencyGuard kafkaIdempotencyGuard;

    @KafkaListener(topics = KafkaTopicConstants.AI_GRADING_RESULTS, groupId = GROUP_ID)
    public void onGradingCompleted(LivePracticeAiGradingCompletedEvent event, Acknowledgment acknowledgment) {
        if (event == null || event.eventId() == null) {
            acknowledgment.acknowledge();
            return;
        }
        if (!kafkaIdempotencyGuard.tryAcquire(GROUP_ID, event.eventId())) {
            acknowledgment.acknowledge();
            return;
        }

        applyResult(event);
        acknowledgment.acknowledge();
    }

    private void applyResult(LivePracticeAiGradingCompletedEvent event) {
        LivePracticeSubmission submission = livePracticeSubmissionRepository.findById(event.submissionId())
                .orElse(null);
        if (submission == null) {
            log.warn("Skip AI grading result: submission {} not found", event.submissionId());
            return;
        }
        if (!Objects.equals(submission.getQuestionSnapshotId(), event.questionSnapshotId())) {
            log.warn("Skip AI grading result: question mismatch for submission {}", event.submissionId());
            return;
        }
        if (Objects.equals(submission.getAiGradingStatus(), LivePracticeAiGradingStatus.COMPLETED.name())) {
            return;
        }

        LivePracticeAiGradingStatus status = parseStatus(event.status());
        if (status == LivePracticeAiGradingStatus.COMPLETED) {
            BigDecimal maxScore = livePracticeQuestionRepository.findById(submission.getQuestionSnapshotId())
                    .map(LivePracticeQuestion::getScore)
                    .orElse(BigDecimal.ZERO);
            BigDecimal earnedScore = clampScore(event.earnedScore(), maxScore);
            submission.setEarnedScore(earnedScore);
            submission.setIsCorrect(Objects.equals(event.isCorrect(), FLAG_ON) ? FLAG_ON : FLAG_OFF);
            submission.setAiGradingStatus(LivePracticeAiGradingStatus.COMPLETED.name());
            submission.setAiGradingFeedback(event.feedback());
            submission.setAiGradingError(null);
        } else {
            submission.setAiGradingStatus(LivePracticeAiGradingStatus.FAILED.name());
            submission.setAiGradingFeedback(event.feedback());
            submission.setAiGradingError(event.errorMessage());
        }
        submission.setAiGradedAt(event.timestamp() != null ? event.timestamp() : Instant.now());
        livePracticeSubmissionRepository.update(submission);
        notifyStudent(submission);
    }

    private LivePracticeAiGradingStatus parseStatus(String status) {
        try {
            return LivePracticeAiGradingStatus.valueOf(status);
        } catch (RuntimeException exception) {
            return LivePracticeAiGradingStatus.FAILED;
        }
    }

    private BigDecimal clampScore(BigDecimal score, BigDecimal maxScore) {
        BigDecimal safeScore = score != null ? score : BigDecimal.ZERO;
        BigDecimal safeMax = maxScore != null ? maxScore : BigDecimal.ZERO;
        if (safeScore.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (safeMax.compareTo(BigDecimal.ZERO) > 0 && safeScore.compareTo(safeMax) > 0) {
            return safeMax;
        }
        return safeScore;
    }

    private void notifyStudent(LivePracticeSubmission submission) {
        livePracticeGroupRepository.findById(submission.getGroupId())
                .ifPresent(group -> livePracticeSseEmitter.sendToUsers(
                        List.of(submission.getStudentId()),
                        toEvent(group)
                ));
    }

    private com.dayz.sc.course.model.vo.LivePracticeEventVO toEvent(LivePracticeGroup group) {
        int totalQuestions = livePracticeQuestionRepository.findByGroupId(group.getId()).size();
        return new com.dayz.sc.course.model.vo.LivePracticeEventVO(
                group.getId(),
                group.getCourseId(),
                group.getClassSessionId(),
                group.getTitle(),
                totalQuestions,
                group.getAvailableStartAt(),
                group.getAvailableEndAt(),
                group.getAllowLateSubmission(),
                group.getPublishedAt()
        );
    }
}
