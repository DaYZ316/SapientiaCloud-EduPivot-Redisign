package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface MessageRepository {

    ChatMessage save(ChatMessage message);

    List<ChatMessage> findByConversationId(UUID conversationId);

    List<ChatMessage> findRecentByConversationId(UUID conversationId, int limit);

    void deleteByConversationId(UUID conversationId);
}
