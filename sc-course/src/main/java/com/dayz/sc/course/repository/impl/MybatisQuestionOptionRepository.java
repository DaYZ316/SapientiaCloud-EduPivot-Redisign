package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.QuestionOptionMapper;
import com.dayz.sc.course.model.entity.QuestionOption;
import com.dayz.sc.course.repository.QuestionOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MybatisQuestionOptionRepository implements QuestionOptionRepository {

    private final QuestionOptionMapper questionOptionMapper;

    @Override
    public Optional<QuestionOption> findById(UUID id) {
        return Optional.ofNullable(questionOptionMapper.selectById(id));
    }

    @Override
    public void save(QuestionOption option) {
        questionOptionMapper.insert(option);
    }

    @Override
    public void saveBatch(List<QuestionOption> options) {
        for (QuestionOption option : options) {
            questionOptionMapper.insert(option);
        }
    }

    @Override
    public void update(QuestionOption option) {
        questionOptionMapper.updateById(option);
    }

    @Override
    public void deleteById(UUID id) {
        questionOptionMapper.deleteById(id);
    }

    @Override
    public void deleteByQuestionId(UUID questionId) {
        LambdaQueryWrapper<QuestionOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionOption::getQuestionId, questionId);
        questionOptionMapper.delete(wrapper);
    }

    @Override
    public List<QuestionOption> findByQuestionId(UUID questionId) {
        LambdaQueryWrapper<QuestionOption> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionOption::getQuestionId, questionId);
        wrapper.orderByAsc(QuestionOption::getOptionLabel);
        return questionOptionMapper.selectList(wrapper);
    }
}
