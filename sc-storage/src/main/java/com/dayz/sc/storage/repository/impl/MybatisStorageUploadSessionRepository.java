package com.dayz.sc.storage.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.storage.mapper.StorageUploadSessionMapper;
import com.dayz.sc.storage.model.entity.StorageUploadSession;
import com.dayz.sc.storage.repository.StorageUploadSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 上传会话仓储 MyBatis-Plus 实现
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Repository
@RequiredArgsConstructor
public class MybatisStorageUploadSessionRepository implements StorageUploadSessionRepository {

    private final StorageUploadSessionMapper uploadSessionMapper;

    @Override
    public Optional<StorageUploadSession> findById(UUID id) {
        return Optional.ofNullable(uploadSessionMapper.selectById(id));
    }

    @Override
    public StorageUploadSession save(StorageUploadSession session) {
        uploadSessionMapper.insert(session);
        return session;
    }

    @Override
    public void update(StorageUploadSession session) {
        uploadSessionMapper.updateById(session);
    }

    @Override
    public Optional<StorageUploadSession> findByObjectId(UUID objectId) {
        LambdaQueryWrapper<StorageUploadSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageUploadSession::getObjectId, objectId);
        wrapper.eq(StorageUploadSession::getStatus, "PENDING");
        wrapper.orderByDesc(StorageUploadSession::getCreatedAt);
        wrapper.last("LIMIT 1");
        return Optional.ofNullable(uploadSessionMapper.selectOne(wrapper));
    }

    @Override
    public List<StorageUploadSession> findExpiredPending(Instant expiredBefore, int limit) {
        LambdaQueryWrapper<StorageUploadSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageUploadSession::getStatus, "PENDING");
        wrapper.lt(StorageUploadSession::getExpiresAt, expiredBefore);
        wrapper.last("LIMIT " + limit);
        return uploadSessionMapper.selectList(wrapper);
    }
}
