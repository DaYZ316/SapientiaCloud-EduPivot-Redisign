package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.PracticeAnswerMapper;
import com.dayz.sc.course.model.entity.PracticeAnswer;
import com.dayz.sc.course.repository.PracticeAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Repository
@RequiredArgsConstructor
public class MybatisPracticeAnswerRepository implements PracticeAnswerRepository {

    private final PracticeAnswerMapper practiceAnswerMapper;

    @Override
    public void save(PracticeAnswer answer) {
        practiceAnswerMapper.insert(answer);
    }

    @Override
    public List<PracticeAnswer> findBySessionId(UUID sessionId) {
        LambdaQueryWrapper<PracticeAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeAnswer::getSessionId, sessionId);
        wrapper.orderByAsc(PracticeAnswer::getAnsweredAt);
        return practiceAnswerMapper.selectList(wrapper);
    }

    @Override
    public List<PracticeAnswer> findByQuestionId(UUID questionId) {
        LambdaQueryWrapper<PracticeAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeAnswer::getQuestionId, questionId);
        return practiceAnswerMapper.selectList(wrapper);
    }
}
