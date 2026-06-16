package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.CreatePracticeSessionRequest;
import com.dayz.sc.course.model.dto.SubmitAnswerRequest;
import com.dayz.sc.course.model.entity.PracticeAnswer;
import com.dayz.sc.course.model.entity.PracticeSession;
import com.dayz.sc.course.model.entity.Question;
import com.dayz.sc.course.model.entity.QuestionBank;
import com.dayz.sc.course.model.entity.QuestionOption;
import com.dayz.sc.course.model.vo.PracticeAnswerVO;
import com.dayz.sc.course.model.vo.PracticeSessionVO;
import com.dayz.sc.course.repository.PracticeAnswerRepository;
import com.dayz.sc.course.repository.PracticeSessionRepository;
import com.dayz.sc.course.repository.QuestionBankRepository;
import com.dayz.sc.course.repository.QuestionOptionRepository;
import com.dayz.sc.course.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Service
@RequiredArgsConstructor
public class PracticeSessionService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeAnswerRepository practiceAnswerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;

    @Transactional(rollbackFor = Exception.class)
    public UUID createPracticeSession(CreatePracticeSessionRequest request, UUID userId) {
        QuestionBank bank = questionBankRepository.findById(request.questionBankId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        int questionCount = (int) questionRepository.countByQuestionBankId(request.questionBankId());
        if (questionCount == 0) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        BigDecimal totalScore = questionRepository.sumScoreByQuestionBankId(request.questionBankId());

        PracticeSession session = new PracticeSession();
        session.setId(UuidV7Generator.generate());
        session.setQuestionBankId(request.questionBankId());
        session.setCourseId(bank.getCourseId());
        session.setSysUserId(userId);
        session.setSessionType(request.sessionType());
        session.setTotalQuestions(questionCount);
        session.setAnsweredCount(0);
        session.setCorrectCount(0);
        session.setTotalScore(totalScore != null ? totalScore : BigDecimal.ZERO);
        session.setEarnedScore(BigDecimal.ZERO);
        session.setStartedAt(Instant.now());
        session.setStatus(0);

        practiceSessionRepository.save(session);
        return session.getId();
    }

    public PracticeSessionVO getPracticeSession(UUID sessionId, UUID userId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!session.getSysUserId().equals(userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        List<PracticeAnswerVO> answers = practiceAnswerRepository.findBySessionId(sessionId).stream()
                .map(this::toPracticeAnswerVO)
                .toList();

        return toPracticeSessionVO(session, answers);
    }

    @Transactional(rollbackFor = Exception.class)
    public PracticeAnswerVO submitAnswer(UUID sessionId, SubmitAnswerRequest request, UUID userId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!session.getSysUserId().equals(userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        if (session.getStatus() != 0) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        boolean isCorrect = false;
        BigDecimal earnedScore = BigDecimal.ZERO;

        int questionType = question.getQuestionType();

        if (questionType == 0 || questionType == 1 || questionType == 2) {
            List<QuestionOption> correctOptions = questionOptionRepository.findByQuestionId(request.questionId())
                    .stream()
                    .filter(o -> o.getIsCorrect() == 1)
                    .toList();

            Set<UUID> correctIds = new HashSet<>(correctOptions.stream().map(QuestionOption::getId).toList());
            Set<UUID> selectedIds = new HashSet<>(request.selectedOptionIds() != null ? request.selectedOptionIds() : List.of());

            isCorrect = correctIds.equals(selectedIds);
            if (isCorrect) {
                earnedScore = question.getScore();
            } else if (question.getAllowPartialCredit() == 1 && questionType == 1) {
                long correctSelected = selectedIds.stream().filter(correctIds::contains).count();
                if (correctIds.size() > 0) {
                    earnedScore = question.getScore().multiply(BigDecimal.valueOf(correctSelected))
                            .divide(BigDecimal.valueOf(correctIds.size()), 2, BigDecimal.ROUND_HALF_UP);
                }
            }
        } else {
            isCorrect = true;
            earnedScore = question.getScore();
        }

        PracticeAnswer answer = new PracticeAnswer();
        answer.setId(UuidV7Generator.generate());
        answer.setSessionId(sessionId);
        answer.setQuestionId(request.questionId());
        answer.setSelectedOptionIds(request.selectedOptionIds());
        answer.setTextAnswer(request.textAnswer());
        answer.setIsCorrect(isCorrect ? 1 : 0);
        answer.setEarnedScore(earnedScore);
        answer.setAnsweredAt(Instant.now());

        practiceAnswerRepository.save(answer);

        session.setAnsweredCount(session.getAnsweredCount() + 1);
        session.setEarnedScore(session.getEarnedScore().add(earnedScore));
        if (isCorrect) {
            session.setCorrectCount(session.getCorrectCount() + 1);
        }
        practiceSessionRepository.update(session);

        return toPracticeAnswerVO(answer);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completePracticeSession(UUID sessionId, UUID userId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!session.getSysUserId().equals(userId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        session.setCompletedAt(Instant.now());
        session.setStatus(1);
        practiceSessionRepository.update(session);
    }

    public List<PracticeSessionVO> getMyPracticeHistory(UUID userId) {
        return practiceSessionRepository.findBySysUserId(userId).stream()
                .map(s -> toPracticeSessionVO(s, null))
                .toList();
    }

    public PracticeSessionVO getBankPracticeStats(UUID bankId) {
        List<PracticeSession> sessions = practiceSessionRepository.findByQuestionBankId(bankId);
        long totalSessions = sessions.size();
        long completedSessions = sessions.stream().filter(s -> s.getStatus() == 1).count();
        int totalCorrect = sessions.stream().mapToInt(PracticeSession::getCorrectCount).sum();
        int totalAnswered = sessions.stream().mapToInt(PracticeSession::getAnsweredCount).sum();

        PracticeSession stats = new PracticeSession();
        stats.setId(UUID.randomUUID());
        stats.setTotalQuestions((int) totalSessions);
        stats.setAnsweredCount(totalAnswered);
        stats.setCorrectCount(totalCorrect);
        stats.setEarnedScore(BigDecimal.ZERO);
        stats.setTotalScore(BigDecimal.ZERO);

        return toPracticeSessionVO(stats, null);
    }

    private PracticeSessionVO toPracticeSessionVO(PracticeSession session, List<PracticeAnswerVO> answers) {
        return new PracticeSessionVO(
                session.getId(),
                session.getQuestionBankId(),
                session.getCourseId(),
                session.getSysUserId(),
                session.getSessionType(),
                session.getTotalQuestions(),
                session.getAnsweredCount(),
                session.getCorrectCount(),
                session.getTotalScore(),
                session.getEarnedScore(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getStatus(),
                answers
        );
    }

    private PracticeAnswerVO toPracticeAnswerVO(PracticeAnswer answer) {
        return new PracticeAnswerVO(
                answer.getId(),
                answer.getSessionId(),
                answer.getQuestionId(),
                answer.getSelectedOptionIds(),
                answer.getTextAnswer(),
                answer.getIsCorrect(),
                answer.getEarnedScore(),
                answer.getAnsweredAt()
        );
    }
}
