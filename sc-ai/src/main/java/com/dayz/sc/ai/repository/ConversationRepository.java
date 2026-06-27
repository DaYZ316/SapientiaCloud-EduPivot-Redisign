package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 会话仓储
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public interface ConversationRepository {

    /**
     * 保存会话
     *
     * @param conversation 会话实体
     */
    void save(Conversation conversation);

    /**
     * 根据会话ID和用户ID查询会话
     *
     * @param id     会话ID
     * @param userId 用户ID
     * @return 会话实体，可能为空
     */
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    /**
     * 根据用户ID分页查询会话列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 会话列表
     */
    List<Conversation> findByUserId(UUID userId, int page, int size);

    /**
     * 更新会话
     *
     * @param conversation 会话实体
     */
    void update(Conversation conversation);

    /**
     * 更新会话的最后更新时间
     *
     * @param id     会话ID
     * @param userId 用户ID
     */
    void touchUpdatedAt(UUID id, UUID userId);

    /**
     * 根据会话ID和用户ID删除会话
     *
     * @param id     会话ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteByIdAndUserId(UUID id, UUID userId);
}
