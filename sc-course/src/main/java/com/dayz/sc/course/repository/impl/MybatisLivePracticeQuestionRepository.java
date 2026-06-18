package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.LivePracticeQuestionMapper;
import com.dayz.sc.course.model.entity.LivePracticeQuestion;
import com.dayz.sc.course.repository.LivePracticeQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 随堂练习题目快照仓储MyBatis-Plus实现
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Repository
@RequiredArgsConstructor
public class MybatisLivePracticeQuestionRepository implements LivePracticeQuestionRepository {

    private final LivePracticeQuestionMapper livePracticeQuestionMapper;

    @Override
    public Optional<LivePracticeQuestion> findById(UUID id) {
        return Optional.ofNullable(livePracticeQuestionMapper.selectById(id));
    }

    @Override
    public void saveBatch(List<LivePracticeQuestion> questions) {
        questions.forEach(livePracticeQuestionMapper::insert);
    }

    @Override
    public List<LivePracticeQuestion> findByGroupId(UUID groupId) {
        LambdaQueryWrapper<LivePracticeQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeQuestion::getGroupId, groupId);
        wrapper.orderByAsc(LivePracticeQuestion::getQuestionOrder);
        return livePracticeQuestionMapper.selectList(wrapper);
    }

    @Override
    public List<LivePracticeQuestion> findByCourseId(UUID courseId) {
        LambdaQueryWrapper<LivePracticeQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeQuestion::getCourseId, courseId);
        wrapper.orderByDesc(LivePracticeQuestion::getCreatedAt);
        return livePracticeQuestionMapper.selectList(wrapper);
    }
}
