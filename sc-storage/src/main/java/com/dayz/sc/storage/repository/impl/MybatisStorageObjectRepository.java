package com.dayz.sc.storage.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.storage.mapper.StorageObjectMapper;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.repository.StorageObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 存储对象仓储 MyBatis-Plus 实现
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Repository
@RequiredArgsConstructor
public class MybatisStorageObjectRepository implements StorageObjectRepository {

    private final StorageObjectMapper storageObjectMapper;

    @Override
    public Optional<StorageObject> findById(UUID id) {
        return Optional.ofNullable(storageObjectMapper.selectById(id));
    }

    @Override
    public StorageObject save(StorageObject obj) {
        storageObjectMapper.insert(obj);
        return obj;
    }

    @Override
    public void update(StorageObject obj) {
        storageObjectMapper.updateById(obj);
    }

    @Override
    public Optional<StorageObject> findByObjectKey(String objectKey) {
        LambdaQueryWrapper<StorageObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageObject::getObjectKey, objectKey);
        return Optional.ofNullable(storageObjectMapper.selectOne(wrapper));
    }

    @Override
    public List<StorageObject> findByScopeAndUsage(String scopeType, UUID scopeId, String usage) {
        LambdaQueryWrapper<StorageObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageObject::getScopeType, scopeType);
        wrapper.eq(StorageObject::getScopeId, scopeId);
        wrapper.eq(StorageObject::getUsage, usage);
        return storageObjectMapper.selectList(wrapper);
    }

    @Override
    public List<StorageObject> findByIdsAndStatus(List<UUID> ids, String status) {
        LambdaQueryWrapper<StorageObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(StorageObject::getId, ids);
        wrapper.eq(StorageObject::getStatus, status);
        return storageObjectMapper.selectList(wrapper);
    }

    @Override
    public List<StorageObject> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<StorageObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(StorageObject::getId, ids);
        return storageObjectMapper.selectList(wrapper);
    }

    @Override
    public List<StorageObject> findPendingByExpired(Instant expiredBefore, int limit) {
        LambdaQueryWrapper<StorageObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageObject::getStatus, "PENDING");
        wrapper.lt(StorageObject::getCreatedAt, expiredBefore);
        wrapper.last("LIMIT " + limit);
        return storageObjectMapper.selectList(wrapper);
    }

    @Override
    public void softDelete(UUID id) {
        StorageObject obj = new StorageObject();
        obj.setId(id);
        obj.setDeleted(StorageObject.DELETED);
        obj.setDeletedAt(Instant.now());
        storageObjectMapper.updateById(obj);
    }
}
