package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.QuestionMapper;
import com.dayz.sc.course.model.entity.Question;
import com.dayz.sc.course.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisQuestionRepository implements QuestionRepository {

    private final QuestionMapper questionMapper;

    @Override
    public Optional<Question> findById(UUID id) {
        return Optional.ofNullable(questionMapper.selectById(id));
    }

    @Override
    public void save(Question question) {
        questionMapper.insert(question);
    }

    @Override
    public void update(Question question) {
        questionMapper.updateById(question);
    }

    @Override
    public void deleteById(UUID id) {
        questionMapper.deleteById(id);
    }

    @Override
    public Page<Question> findByQuestionBankId(UUID questionBankId, int page, int size) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getQuestionBankId, questionBankId);
        wrapper.orderByDesc(Question::getCreatedAt);
        return questionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public Page<Question> findAll(int page, int size, UUID questionBankId, UUID courseId,
                                   Integer questionType, Integer difficulty, Integer status, String keyword) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (questionBankId != null) {
            wrapper.eq(Question::getQuestionBankId, questionBankId);
        }
        if (courseId != null) {
            wrapper.eq(Question::getCourseId, courseId);
        }
        if (questionType != null) {
            wrapper.eq(Question::getQuestionType, questionType);
        }
        if (difficulty != null) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }
        if (status != null) {
            wrapper.eq(Question::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Question::getQuestionTitle, keyword);
        }
        wrapper.orderByDesc(Question::getCreatedAt);
        return questionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public long countByQuestionBankId(UUID questionBankId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getQuestionBankId, questionBankId);
        return questionMapper.selectCount(wrapper);
    }

    @Override
    public Map<UUID, Long> countByQuestionBankIds(List<UUID> bankIds) {
        if (bankIds == null || bankIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> results = questionMapper.countByQuestionBankIds(bankIds);
        return results.stream()
                .collect(Collectors.toMap(
                        m -> UUID.fromString(m.get("question_bank_id").toString()),
                        m -> ((Number) m.get("cnt")).longValue(),
                        (a, b) -> a
                ));
    }

    @Override
    public BigDecimal sumScoreByQuestionBankId(UUID questionBankId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getQuestionBankId, questionBankId);
        wrapper.select(Question::getScore);
        List<Question> questions = questionMapper.selectList(wrapper);
        return questions.stream()
                .map(Question::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
