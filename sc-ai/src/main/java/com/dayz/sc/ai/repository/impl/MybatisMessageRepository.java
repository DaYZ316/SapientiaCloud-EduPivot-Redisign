package com.dayz.sc.ai.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.ai.mapper.MessageMapper;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 消息仓储 MyBatis-Plus 实现。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Repository
@RequiredArgsConstructor
public class MybatisMessageRepository implements MessageRepository {

    private final MessageMapper messageMapper;

    @Override
    public ChatMessage save(ChatMessage message) {
        messageMapper.insert(message);
        return message;
    }

    @Override
    public List<ChatMessage> findByConversationId(UUID conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId);
        wrapper.orderByAsc(ChatMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    @Override
    public List<ChatMessage> findRecentByConversationId(UUID conversationId, int limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId);
        wrapper.orderByDesc(ChatMessage::getCreatedAt);
        wrapper.last("LIMIT " + limit);
        return messageMapper.selectList(wrapper);
    }

    @Override
    public void deleteByConversationId(UUID conversationId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getConversationId, conversationId);
        messageMapper.delete(wrapper);
    }
}
