package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.QuestionBankMapper;
import com.dayz.sc.course.model.entity.QuestionBank;
import com.dayz.sc.course.repository.QuestionBankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Repository
@RequiredArgsConstructor
public class MybatisQuestionBankRepository implements QuestionBankRepository {

    private final QuestionBankMapper questionBankMapper;

    @Override
    public Optional<QuestionBank> findById(UUID id) {
        return Optional.ofNullable(questionBankMapper.selectById(id));
    }

    @Override
    public void save(QuestionBank bank) {
        questionBankMapper.insert(bank);
    }

    @Override
    public void update(QuestionBank bank) {
        questionBankMapper.updateById(bank);
    }

    @Override
    public void deleteById(UUID id) {
        questionBankMapper.deleteById(id);
    }

    @Override
    public List<QuestionBank> findByCourseId(UUID courseId) {
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionBank::getCourseId, courseId);
        wrapper.orderByDesc(QuestionBank::getCreatedAt);
        return questionBankMapper.selectList(wrapper);
    }

    @Override
    public Page<QuestionBank> findAll(int page, int size, UUID courseId, Integer bankType, String keyword) {
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(QuestionBank::getCourseId, courseId);
        }
        if (bankType != null) {
            wrapper.eq(QuestionBank::getBankType, bankType);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(QuestionBank::getBankName, keyword);
        }
        wrapper.orderByDesc(QuestionBank::getCreatedAt);
        return questionBankMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public long countByCourseId(UUID courseId) {
        LambdaQueryWrapper<QuestionBank> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionBank::getCourseId, courseId);
        return questionBankMapper.selectCount(wrapper);
    }
}
