package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.KnowledgeDoc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识库文档仓储
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public interface KnowledgeDocRepository {

    /**
     * 保存知识库文档
     *
     * @param doc 文档实体
     */
    void save(KnowledgeDoc doc);

    /**
     * 根据文档ID和用户ID查询文档
     *
     * @param id     文档ID
     * @param userId 用户ID
     * @return 文档实体，可能为空
     */
    Optional<KnowledgeDoc> findByIdAndUserId(UUID id, UUID userId);

    /**
     * 根据用户ID分页查询文档列表
     *
     * @param userId 用户ID
     * @param page   页码
     * @param size   每页大小
     * @return 文档列表
     */
    List<KnowledgeDoc> findByUserId(UUID userId, int page, int size);

    /**
     * 更新知识库文档
     *
     * @param doc 文档实体
     */
    void update(KnowledgeDoc doc);

    /**
     * 根据文档ID和用户ID删除文档
     *
     * @param id     文档ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteByIdAndUserId(UUID id, UUID userId);
}
