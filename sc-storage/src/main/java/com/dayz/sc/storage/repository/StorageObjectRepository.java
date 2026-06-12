package com.dayz.sc.storage.repository;

import com.dayz.sc.storage.model.entity.StorageObject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 存储对象仓储接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface StorageObjectRepository {

    Optional<StorageObject> findById(UUID id);

    StorageObject save(StorageObject obj);

    void update(StorageObject obj);

    Optional<StorageObject> findByObjectKey(String objectKey);

    List<StorageObject> findByScopeAndUsage(String scopeType, UUID scopeId, String usage);

    List<StorageObject> findByIdsAndStatus(List<UUID> ids, String status);

    List<StorageObject> findByIds(List<UUID> ids);

    List<StorageObject> findPendingByExpired(Instant expiredBefore, int limit);

    void softDelete(UUID id);
}
