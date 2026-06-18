package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话仓储。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public interface ConversationRepository {

    void save(Conversation conversation);

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    List<Conversation> findByUserId(UUID userId, int page, int size);

    void update(Conversation conversation);

    boolean deleteByIdAndUserId(UUID id, UUID userId);
}
