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

    /**
     * 根据ID查询存储对象
     *
     * @param id 对象ID
     * @return 对象实体，可能为空
     */
    Optional<StorageObject> findById(UUID id);

    /**
     * 保存存储对象
     *
     * @param obj 对象实体
     * @return 保存后的对象实体
     */
    StorageObject save(StorageObject obj);

    /**
     * 更新存储对象
     *
     * @param obj 对象实体
     */
    void update(StorageObject obj);

    /**
     * 根据对象Key查询存储对象
     *
     * @param objectKey 对象Key
     * @return 对象实体，可能为空
     */
    Optional<StorageObject> findByObjectKey(String objectKey);

    /**
     * 根据范围类型、范围ID和用途查询存储对象
     *
     * @param scopeType 范围类型
     * @param scopeId   范围ID
     * @param usage     用途
     * @return 对象列表
     */
    List<StorageObject> findByScopeAndUsage(String scopeType, UUID scopeId, String usage);

    /**
     * 根据ID列表和状态查询存储对象
     *
     * @param ids    ID列表
     * @param status 状态
     * @return 对象列表
     */
    List<StorageObject> findByIdsAndStatus(List<UUID> ids, String status);

    /**
     * 根据ID列表查询存储对象
     *
     * @param ids ID列表
     * @return 对象列表
     */
    List<StorageObject> findByIds(List<UUID> ids);

    /**
     * 查询过期的待处理存储对象
     *
     * @param expiredBefore 过期时间
     * @param limit         限制数量
     * @return 对象列表
     */
    List<StorageObject> findPendingByExpired(Instant expiredBefore, int limit);

    /**
     * 软删除存储对象
     *
     * @param id 对象ID
     */
    void softDelete(UUID id);
}
