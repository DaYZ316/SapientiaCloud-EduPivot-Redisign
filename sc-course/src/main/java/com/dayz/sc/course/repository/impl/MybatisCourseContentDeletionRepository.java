package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.*;
import com.dayz.sc.course.model.entity.*;
import com.dayz.sc.course.repository.CourseContentDeletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * MyBatis implementation for deleting course-owned content in dependency order.
 *
 * @author DaYZ
 */
@Repository
@RequiredArgsConstructor
public class MybatisCourseContentDeletionRepository implements CourseContentDeletionRepository {

    private final ChapterMapper chapterMapper;
    private final ChapterLikeMapper chapterLikeMapper;
    private final CourseFileMapper courseFileMapper;
    private final ClassSessionMapper classSessionMapper;
    private final ClassParticipantMapper classParticipantMapper;
    private final ClassBarrageMapper classBarrageMapper;
    private final ForumPostMapper forumPostMapper;
    private final ForumReplyMapper forumReplyMapper;
    private final QuestionBankMapper questionBankMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final LivePracticeGroupMapper livePracticeGroupMapper;
    private final LivePracticeQuestionMapper livePracticeQuestionMapper;

    @Override
    public void deleteCourseContent(UUID courseId) {
        deleteClassSessionDependentsByCourseId(courseId);
        chapterLikeMapper.delete(new LambdaQueryWrapper<ChapterLike>()
                .eq(ChapterLike::getCourseId, courseId));
        chapterMapper.delete(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCourseId, courseId));
        courseFileMapper.delete(new LambdaQueryWrapper<CourseFile>()
                .eq(CourseFile::getCourseId, courseId));
        forumReplyMapper.delete(new LambdaQueryWrapper<ForumReply>()
                .eq(ForumReply::getCourseId, courseId));
        forumPostMapper.delete(new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getCourseId, courseId));
        deleteQuestionsByCourseId(courseId);
        questionBankMapper.delete(new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getCourseId, courseId));
        livePracticeQuestionMapper.delete(new LambdaQueryWrapper<LivePracticeQuestion>()
                .eq(LivePracticeQuestion::getCourseId, courseId));
        livePracticeGroupMapper.delete(new LambdaQueryWrapper<LivePracticeGroup>()
                .eq(LivePracticeGroup::getCourseId, courseId));
        classSessionMapper.delete(new LambdaQueryWrapper<ClassSession>()
                .eq(ClassSession::getCourseId, courseId));
    }

    @Override
    public void deleteQuestionBankContent(UUID questionBankId) {
        deleteQuestionsByQuestionBankId(questionBankId);
    }

    @Override
    public void deleteClassSessionContent(UUID sessionId) {
        classParticipantMapper.delete(new LambdaQueryWrapper<ClassParticipant>()
                .eq(ClassParticipant::getSessionId, sessionId));
        classBarrageMapper.delete(new LambdaQueryWrapper<ClassBarrage>()
                .eq(ClassBarrage::getSessionId, sessionId));
        livePracticeQuestionMapper.delete(new LambdaQueryWrapper<LivePracticeQuestion>()
                .eq(LivePracticeQuestion::getClassSessionId, sessionId));
        livePracticeGroupMapper.delete(new LambdaQueryWrapper<LivePracticeGroup>()
                .eq(LivePracticeGroup::getClassSessionId, sessionId));
    }

    private void deleteClassSessionDependentsByCourseId(UUID courseId) {
        List<UUID> sessionIds = classSessionMapper.selectList(new LambdaQueryWrapper<ClassSession>()
                        .eq(ClassSession::getCourseId, courseId)
                        .select(ClassSession::getId))
                .stream()
                .map(ClassSession::getId)
                .toList();
        if (sessionIds.isEmpty()) {
            return;
        }
        classParticipantMapper.delete(new LambdaQueryWrapper<ClassParticipant>()
                .in(ClassParticipant::getSessionId, sessionIds));
        classBarrageMapper.delete(new LambdaQueryWrapper<ClassBarrage>()
                .in(ClassBarrage::getSessionId, sessionIds));
    }

    private void deleteQuestionsByCourseId(UUID courseId) {
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getCourseId, courseId));
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>()
                .eq(QuestionAnswer::getCourseId, courseId));
        questionMapper.delete(new LambdaQueryWrapper<Question>()
                .eq(Question::getCourseId, courseId));
    }

    private void deleteQuestionsByQuestionBankId(UUID questionBankId) {
        List<UUID> questionIds = questionMapper.selectList(new LambdaQueryWrapper<Question>()
                        .eq(Question::getQuestionBankId, questionBankId)
                        .select(Question::getId))
                .stream()
                .map(Question::getId)
                .toList();
        if (questionIds.isEmpty()) {
            return;
        }
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .in(QuestionOption::getQuestionId, questionIds));
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>()
                .in(QuestionAnswer::getQuestionId, questionIds));
        questionMapper.delete(new LambdaQueryWrapper<Question>()
                .in(Question::getId, questionIds));
    }
}
