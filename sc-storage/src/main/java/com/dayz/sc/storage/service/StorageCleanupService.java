package com.dayz.sc.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.storage.mapper.StorageObjectMapper;
import com.dayz.sc.storage.mapper.StorageUploadSessionMapper;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.entity.StorageUploadSession;
import com.dayz.sc.storage.model.enums.StorageObjectStatus;
import com.dayz.sc.storage.model.enums.UploadSessionStatus;
import com.dayz.sc.storage.util.StorageObjectKeyBuilder;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageCleanupService {

    private final StorageUploadSessionMapper uploadSessionMapper;
    private final StorageObjectMapper storageObjectMapper;
    private final MinioClient minioClient;

    @Scheduled(fixedDelayString = "${edupivot.storage.cleanup-delay-ms:3600000}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredPendingUploads() {
        LambdaQueryWrapper<StorageUploadSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageUploadSession::getStatus, UploadSessionStatus.PENDING.name());
        wrapper.lt(StorageUploadSession::getExpiresAt, Instant.now());
        wrapper.last("LIMIT 100");
        List<StorageUploadSession> sessions = uploadSessionMapper.selectList(wrapper);
        for (StorageUploadSession session : sessions) {
            expireSession(session);
        }
    }

    private void expireSession(StorageUploadSession session) {
        StorageObject object = storageObjectMapper.selectById(session.getObjectId());
        if (object != null && StorageObjectStatus.PENDING.name().equals(object.getStatus())) {
            String tempKey = StorageObjectKeyBuilder.tempKey(
                    object.getId(), object.getOriginalFilename(), object.getContentType());
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(object.getBucket())
                        .object(tempKey)
                        .build());
            } catch (Exception exception) {
                log.debug("Ignoring expired temp object cleanup failure: {}", object.getId(), exception);
            }
            object.setDeleted(1);
            object.setDeletedAt(Instant.now());
            storageObjectMapper.updateById(object);
        }
        session.setStatus(UploadSessionStatus.EXPIRED.name());
        uploadSessionMapper.updateById(session);
    }
}
