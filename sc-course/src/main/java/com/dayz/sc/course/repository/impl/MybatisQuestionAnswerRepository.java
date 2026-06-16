package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.QuestionAnswerMapper;
import com.dayz.sc.course.model.entity.QuestionAnswer;
import com.dayz.sc.course.repository.QuestionAnswerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Repository
@RequiredArgsConstructor
public class MybatisQuestionAnswerRepository implements QuestionAnswerRepository {

    private final QuestionAnswerMapper questionAnswerMapper;

    @Override
    public Optional<QuestionAnswer> findById(UUID id) {
        return Optional.ofNullable(questionAnswerMapper.selectById(id));
    }

    @Override
    public void save(QuestionAnswer answer) {
        questionAnswerMapper.insert(answer);
    }

    @Override
    public void saveBatch(List<QuestionAnswer> answers) {
        questionAnswerMapper.batchInsert(answers);
    }

    @Override
    public void update(QuestionAnswer answer) {
        questionAnswerMapper.updateById(answer);
    }

    @Override
    public void deleteById(UUID id) {
        questionAnswerMapper.deleteById(id);
    }

    @Override
    public void deleteByQuestionId(UUID questionId) {
        LambdaQueryWrapper<QuestionAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionAnswer::getQuestionId, questionId);
        questionAnswerMapper.delete(wrapper);
    }

    @Override
    public List<QuestionAnswer> findByQuestionId(UUID questionId) {
        LambdaQueryWrapper<QuestionAnswer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionAnswer::getQuestionId, questionId);
        wrapper.orderByAsc(QuestionAnswer::getSortOrder);
        return questionAnswerMapper.selectList(wrapper);
    }
}
