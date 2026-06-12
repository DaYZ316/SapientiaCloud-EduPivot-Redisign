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

    Optional<StorageUploadSession> findById(UUID id);

    StorageUploadSession save(StorageUploadSession session);

    void update(StorageUploadSession session);

    Optional<StorageUploadSession> findByObjectId(UUID objectId);

    List<StorageUploadSession> findExpiredPending(Instant expiredBefore, int limit);
}
