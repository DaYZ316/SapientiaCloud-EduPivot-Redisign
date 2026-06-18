package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

/**
 * 对话消息仓储
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public interface MessageRepository {

    /**
     * 保存对话消息
     *
     * @param message 消息实体
     * @return 保存后的消息实体
     */
    ChatMessage save(ChatMessage message);

    /**
     * 根据会话ID查询所有消息
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<ChatMessage> findByConversationId(UUID conversationId);

    /**
     * 根据会话ID查询最近N条消息
     *
     * @param conversationId 会话ID
     * @param limit          查询条数
     * @return 消息列表
     */
    List<ChatMessage> findRecentByConversationId(UUID conversationId, int limit);

    /**
     * 根据会话ID删除所有消息
     *
     * @param conversationId 会话ID
     */
    void deleteByConversationId(UUID conversationId);
}
