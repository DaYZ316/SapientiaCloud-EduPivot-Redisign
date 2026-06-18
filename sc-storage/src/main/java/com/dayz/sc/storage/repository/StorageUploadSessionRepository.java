package com.dayz.sc.storage.repository;

import com.dayz.sc.storage.model.entity.StorageUploadSession;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 上传会话仓储接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface StorageUploadSessionRepository {

    /**
     * 根据ID查询上传会话
     *
     * @param id 会话ID
     * @return 会话实体，可能为空
     */
    Optional<StorageUploadSession> findById(UUID id);

    /**
     * 保存上传会话
     *
     * @param session 会话实体
     * @return 保存后的会话实体
     */
    StorageUploadSession save(StorageUploadSession session);

    /**
     * 更新上传会话
     *
     * @param session 会话实体
     */
    void update(StorageUploadSession session);

    /**
     * 根据对象ID查询上传会话
     *
     * @param objectId 对象ID
     * @return 会话实体，可能为空
     */
    Optional<StorageUploadSession> findByObjectId(UUID objectId);

    /**
     * 查询过期的待处理上传会话
     *
     * @param expiredBefore 过期时间
     * @param limit         限制数量
     * @return 会话列表
     */
    List<StorageUploadSession> findExpiredPending(Instant expiredBefore, int limit);
}
