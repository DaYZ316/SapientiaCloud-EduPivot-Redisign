package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.ai.mapper.KnowledgeDocMapper;
import com.dayz.sc.ai.model.entity.KnowledgeDoc;
import com.dayz.sc.ai.repository.KnowledgeDocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识库文档仓储 MyBatis-Plus 实现
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Repository
@RequiredArgsConstructor
public class MybatisKnowledgeDocRepository implements KnowledgeDocRepository {

    private final KnowledgeDocMapper knowledgeDocMapper;

    @Override
    public void save(KnowledgeDoc doc) {
        knowledgeDocMapper.insert(doc);
    }

    @Override
    public Optional<KnowledgeDoc> findByIdAndUserId(UUID id, UUID userId) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDoc::getId, id);
        wrapper.eq(KnowledgeDoc::getUserId, userId);
        return Optional.ofNullable(knowledgeDocMapper.selectOne(wrapper));
    }

    @Override
    public List<KnowledgeDoc> findByUserId(UUID userId, int page, int size) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDoc::getUserId, userId);
        wrapper.orderByDesc(KnowledgeDoc::getCreatedAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return knowledgeDocMapper.selectList(wrapper);
    }

    @Override
    public void update(KnowledgeDoc doc) {
        knowledgeDocMapper.updateById(doc);
    }

    @Override
    public boolean deleteByIdAndUserId(UUID id, UUID userId) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeDoc::getId, id);
        wrapper.eq(KnowledgeDoc::getUserId, userId);
        return knowledgeDocMapper.delete(wrapper) > 0;
    }
}
