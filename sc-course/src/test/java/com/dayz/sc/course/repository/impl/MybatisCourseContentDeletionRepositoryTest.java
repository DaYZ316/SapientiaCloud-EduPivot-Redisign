package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.dayz.sc.course.mapper.*;
import com.dayz.sc.course.model.entity.ClassSession;
import com.dayz.sc.course.model.entity.Question;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MybatisCourseContentDeletionRepositoryTest {

    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private ChapterLikeMapper chapterLikeMapper;
    @Mock
    private CourseFileMapper courseFileMapper;
    @Mock
    private ClassSessionMapper classSessionMapper;
    @Mock
    private ClassParticipantMapper classParticipantMapper;
    @Mock
    private ClassBarrageMapper classBarrageMapper;
    @Mock
    private ForumPostMapper forumPostMapper;
    @Mock
    private ForumReplyMapper forumReplyMapper;
    @Mock
    private QuestionBankMapper questionBankMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionOptionMapper questionOptionMapper;
    @Mock
    private QuestionAnswerMapper questionAnswerMapper;
    @Mock
    private LivePracticeGroupMapper livePracticeGroupMapper;
    @Mock
    private LivePracticeQuestionMapper livePracticeQuestionMapper;

    private MybatisCourseContentDeletionRepository repository;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ClassSession.class);
        TableInfoHelper.initTableInfo(assistant, Question.class);
    }

    @BeforeEach
    void setUp() {
        repository = new MybatisCourseContentDeletionRepository(
                chapterMapper,
                chapterLikeMapper,
                courseFileMapper,
                classSessionMapper,
                classParticipantMapper,
                classBarrageMapper,
                forumPostMapper,
                forumReplyMapper,
                questionBankMapper,
                questionMapper,
                questionOptionMapper,
                questionAnswerMapper,
                livePracticeGroupMapper,
                livePracticeQuestionMapper);
    }

    @Test
    void deleteCourseContent_shouldDeleteCourseScopedContentTree() {
        UUID courseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ClassSession session = new ClassSession();
        session.setId(sessionId);
        when(classSessionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(session));

        repository.deleteCourseContent(courseId);

        verify(classParticipantMapper).delete(any(LambdaQueryWrapper.class));
        verify(classBarrageMapper).delete(any(LambdaQueryWrapper.class));
        verify(chapterLikeMapper).delete(any(LambdaQueryWrapper.class));
        verify(chapterMapper).delete(any(LambdaQueryWrapper.class));
        verify(courseFileMapper).delete(any(LambdaQueryWrapper.class));
        verify(forumReplyMapper).delete(any(LambdaQueryWrapper.class));
        verify(forumPostMapper).delete(any(LambdaQueryWrapper.class));
        verify(questionOptionMapper).delete(any(LambdaQueryWrapper.class));
        verify(questionAnswerMapper).delete(any(LambdaQueryWrapper.class));
        verify(questionMapper).delete(any(LambdaQueryWrapper.class));
        verify(questionBankMapper).delete(any(LambdaQueryWrapper.class));
        verify(livePracticeQuestionMapper).delete(any(LambdaQueryWrapper.class));
        verify(livePracticeGroupMapper).delete(any(LambdaQueryWrapper.class));
        verify(classSessionMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void deleteQuestionBankContent_shouldDeleteQuestionsOptionsAndAnswers() {
        UUID questionBankId = UUID.randomUUID();
        Question question = new Question();
        question.setId(UUID.randomUUID());
        when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(question));

        repository.deleteQuestionBankContent(questionBankId);

        verify(questionOptionMapper).delete(any(LambdaQueryWrapper.class));
        verify(questionAnswerMapper).delete(any(LambdaQueryWrapper.class));
        verify(questionMapper).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void deleteQuestionBankContent_shouldSkipChildrenWhenBankHasNoQuestions() {
        when(questionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        repository.deleteQuestionBankContent(UUID.randomUUID());

        verify(questionOptionMapper, never()).delete(any());
        verify(questionAnswerMapper, never()).delete(any());
        verify(questionMapper, never()).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void deleteClassSessionContent_shouldDeleteClassroomAndLivePracticeDependents() {
        UUID sessionId = UUID.randomUUID();

        repository.deleteClassSessionContent(sessionId);

        verify(classParticipantMapper).delete(any(LambdaQueryWrapper.class));
        verify(classBarrageMapper).delete(any(LambdaQueryWrapper.class));
        verify(livePracticeQuestionMapper).delete(any(LambdaQueryWrapper.class));
        verify(livePracticeGroupMapper).delete(any(LambdaQueryWrapper.class));
    }
}
