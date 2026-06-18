package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.ai.mapper.ConversationMapper;
import com.dayz.sc.ai.model.entity.Conversation;
import com.dayz.sc.ai.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话仓储 MyBatis-Plus 实现。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Repository
@RequiredArgsConstructor
public class MybatisConversationRepository implements ConversationRepository {

    private final ConversationMapper conversationMapper;

    @Override
    public void save(Conversation conversation) {
        conversationMapper.insert(conversation);
    }

    @Override
    public Optional<Conversation> findByIdAndUserId(UUID id, UUID userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getId, id);
        wrapper.eq(Conversation::getUserId, userId);
        return Optional.ofNullable(conversationMapper.selectOne(wrapper));
    }

    @Override
    public List<Conversation> findByUserId(UUID userId, int page, int size) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getUserId, userId);
        wrapper.orderByDesc(Conversation::getPinned)
                .orderByDesc(Conversation::getUpdatedAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return conversationMapper.selectList(wrapper);
    }

    @Override
    public void update(Conversation conversation) {
        conversationMapper.updateById(conversation);
    }

    @Override
    public boolean deleteByIdAndUserId(UUID id, UUID userId) {
        LambdaQueryWrapper<Conversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Conversation::getId, id);
        wrapper.eq(Conversation::getUserId, userId);
        return conversationMapper.delete(wrapper) > 0;
    }
}
