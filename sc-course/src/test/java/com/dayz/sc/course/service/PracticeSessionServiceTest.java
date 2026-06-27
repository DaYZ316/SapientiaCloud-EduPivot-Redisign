package com.dayz.sc.course.service;

import com.dayz.sc.course.model.dto.SubmitAnswerRequest;
import com.dayz.sc.course.model.entity.PracticeAnswer;
import com.dayz.sc.course.model.entity.PracticeSession;
import com.dayz.sc.course.model.entity.Question;
import com.dayz.sc.course.model.entity.QuestionOption;
import com.dayz.sc.course.model.vo.PracticeAnswerVO;
import com.dayz.sc.course.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PracticeSessionServiceTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    @Mock
    private PracticeAnswerRepository practiceAnswerRepository;

    @Mock
    private QuestionBankRepository questionBankRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionOptionRepository questionOptionRepository;

    private PracticeSessionService practiceSessionService;

    @BeforeEach
    void setUp() {
        practiceSessionService = new PracticeSessionService(
                practiceSessionRepository,
                practiceAnswerRepository,
                questionBankRepository,
                questionRepository,
                questionOptionRepository,
                mock(CourseContentAccessService.class)
        );
    }

    @Test
    void submitAnswer_shouldGiveZeroScore_whenMultiChoiceIncludesWrongOption() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID correctOptionId = UUID.randomUUID();
        UUID missedCorrectOptionId = UUID.randomUUID();
        UUID wrongOptionId = UUID.randomUUID();
        PracticeSession session = session(sessionId, userId);
        Question question = multiChoiceQuestion(questionId);
        when(practiceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionOptionRepository.findByQuestionId(questionId)).thenReturn(List.of(
                option(correctOptionId, 1),
                option(missedCorrectOptionId, 1),
                option(wrongOptionId, 0)
        ));

        PracticeAnswerVO answer = practiceSessionService.submitAnswer(
                sessionId,
                new SubmitAnswerRequest(questionId, List.of(correctOptionId, wrongOptionId), null),
                userId
        );

        assertThat(answer.isCorrect()).isZero();
        assertThat(answer.earnedScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(session.getAnsweredCount()).isEqualTo(1);
        assertThat(session.getCorrectCount()).isZero();
        assertThat(session.getEarnedScore()).isEqualByComparingTo(BigDecimal.ZERO);
        ArgumentCaptor<PracticeAnswer> answerCaptor = ArgumentCaptor.captor();
        verify(practiceAnswerRepository).save(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getEarnedScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private PracticeSession session(UUID sessionId, UUID userId) {
        PracticeSession session = new PracticeSession();
        session.setId(sessionId);
        session.setSysUserId(userId);
        session.setStatus(0);
        session.setAnsweredCount(0);
        session.setCorrectCount(0);
        session.setEarnedScore(BigDecimal.ZERO);
        return session;
    }

    private Question multiChoiceQuestion(UUID questionId) {
        Question question = new Question();
        question.setId(questionId);
        question.setQuestionType(1);
        question.setScore(BigDecimal.TEN);
        question.setAllowPartialCredit(1);
        return question;
    }

    private QuestionOption option(UUID optionId, int isCorrect) {
        QuestionOption option = new QuestionOption();
        option.setId(optionId);
        option.setIsCorrect(isCorrect);
        return option;
    }
}
