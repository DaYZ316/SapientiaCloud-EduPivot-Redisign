package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.course.model.dto.BatchCreateQuestionsRequest;
import com.dayz.sc.course.model.dto.QuestionImportRequest;
import com.dayz.sc.course.model.entity.Question;
import com.dayz.sc.course.model.entity.QuestionBank;
import com.dayz.sc.course.model.enums.QuestionStatus;
import com.dayz.sc.course.model.vo.BatchCreateQuestionsResponse;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.QuestionAnswerRepository;
import com.dayz.sc.course.repository.QuestionBankRepository;
import com.dayz.sc.course.repository.QuestionOptionRepository;
import com.dayz.sc.course.repository.QuestionRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    @Mock
    private QuestionBankRepository questionBankRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionOptionRepository questionOptionRepository;

    @Mock
    private QuestionAnswerRepository questionAnswerRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    private QuestionBankService questionBankService;

    @BeforeEach
    void setUp() {
        questionBankService = new QuestionBankService(
                questionBankRepository,
                questionRepository,
                questionOptionRepository,
                questionAnswerRepository,
                courseTeacherRepository,
                mock(CourseContentAccessService.class)
        );
    }

    @Test
    void batchCreateQuestions_shouldPersistAllQuestions_whenUserIsCourseTeacher() {
        UUID bankId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank(bankId, courseId)));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)).thenReturn(true);

        BatchCreateQuestionsResponse response = questionBankService.batchCreateQuestions(
                request(bankId, question("Question 1"), question("Question 2")),
                userId,
                2
        );

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.captor();
        verify(questionRepository, times(2)).save(questionCaptor.capture());
        assertThat(response.importedCount()).isEqualTo(2);
        assertThat(response.questionIds()).hasSize(2);
        assertThat(questionCaptor.getAllValues())
                .allSatisfy(question -> {
                    assertThat(question.getQuestionBankId()).isEqualTo(bankId);
                    assertThat(question.getCourseId()).isEqualTo(courseId);
                    assertThat(question.getSysUserId()).isEqualTo(userId);
                    assertThat(question.getStatus()).isEqualTo(QuestionStatus.DRAFT.getCode());
                });
    }

    @Test
    void batchCreateQuestions_shouldRejectNonCourseTeacher() {
        UUID bankId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank(bankId, courseId)));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)).thenReturn(false);

        assertThatThrownBy(() -> questionBankService.batchCreateQuestions(request(bankId, question("Question")), userId, 2))
                .isInstanceOf(BusinessException.class);

        verify(questionRepository, never()).save(any());
    }

    @Test
    void batchCreateQuestions_shouldRejectInvalidQuestionBeforeWritingIt() {
        UUID bankId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank(bankId, courseId)));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)).thenReturn(true);

        assertThatThrownBy(() -> questionBankService.batchCreateQuestions(
                request(bankId, question("Question"), new QuestionImportRequest(
                        "",
                        "content",
                        0,
                        2,
                        BigDecimal.ONE,
                        1,
                        List.of(),
                        List.of(),
                        0,
                        List.of(),
                        List.of()
                )),
                userId,
                2
        )).isInstanceOf(BusinessException.class);

        verify(questionRepository, never()).save(any());
    }

    @Test
    void batchCreateQuestions_shouldAllowAdmin() {
        UUID bankId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(questionBankRepository.findById(bankId)).thenReturn(Optional.of(bank(bankId, courseId)));

        BatchCreateQuestionsResponse response = questionBankService.batchCreateQuestions(
                request(bankId, question("Question")),
                adminId,
                0
        );

        assertThat(response.importedCount()).isEqualTo(1);
        verify(courseTeacherRepository, never()).existsByCourseIdAndTeacherId(courseId, adminId);
        verify(questionRepository).save(any());
    }

    private BatchCreateQuestionsRequest request(UUID bankId, QuestionImportRequest... questions) {
        return new BatchCreateQuestionsRequest(bankId, List.of(questions));
    }

    private QuestionImportRequest question(String title) {
        return new QuestionImportRequest(
                title,
                "content",
                4,
                2,
                BigDecimal.TEN,
                5,
                List.of("AI"),
                List.of(),
                0,
                List.of(),
                List.of()
        );
    }

    private QuestionBank bank(UUID bankId, UUID courseId) {
        QuestionBank bank = new QuestionBank();
        bank.setId(bankId);
        bank.setCourseId(courseId);
        bank.setSysUserId(UUID.randomUUID());
        bank.setBankName("Bank");
        bank.setDifficulty(2);
        return bank;
    }
}
