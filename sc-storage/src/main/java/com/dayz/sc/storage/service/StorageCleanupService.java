package com.dayz.sc.storage.service;

import com.dayz.sc.storage.mapper.StorageObjectMapper;
import com.dayz.sc.storage.mapper.StorageUploadSessionMapper;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.entity.StorageUploadSession;
import com.dayz.sc.storage.model.enums.StorageObjectStatus;
import com.dayz.sc.storage.model.enums.UploadSessionStatus;
import com.dayz.sc.storage.repository.StorageObjectRepository;
import com.dayz.sc.storage.repository.StorageUploadSessionRepository;
import com.dayz.sc.storage.util.StorageObjectKeyBuilder;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StorageCleanupService {

    private final StorageUploadSessionRepository uploadSessionRepository;
    private final StorageObjectRepository storageObjectRepository;
    private final StorageObjectMapper storageObjectMapper;
    private final StorageUploadSessionMapper uploadSessionMapper;
    private final MinioClient minioClient;

    @Scheduled(fixedDelayString = "${edupivot.storage.cleanup-delay-ms:3600000}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredPendingUploads() {
        List<StorageUploadSession> sessions = uploadSessionRepository.findExpiredPending(Instant.now(), 100);
        if (sessions.isEmpty()) {
            return;
        }

        // 批量查询关联的存储对象
        List<UUID> objectIds = sessions.stream().map(StorageUploadSession::getObjectId).toList();
        Map<UUID, StorageObject> objectMap = storageObjectRepository.findByIds(objectIds).stream()
                .collect(java.util.stream.Collectors.toMap(StorageObject::getId, o -> o));

        // 逐个清理 MinIO 临时对象
        List<UUID> objectIdsToDelete = new ArrayList<>();
        for (StorageUploadSession session : sessions) {
            StorageObject object = objectMap.get(session.getObjectId());
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
                objectIdsToDelete.add(object.getId());
            }
        }

        // 批量软删除存储对象
        if (!objectIdsToDelete.isEmpty()) {
            Instant now = Instant.now();
            storageObjectMapper.batchUpdateDeleted(objectIdsToDelete, StorageObject.DELETED, now);
        }

        // 批量更新上传会话状态
        List<UUID> sessionIds = sessions.stream().map(StorageUploadSession::getId).toList();
        uploadSessionMapper.batchUpdateStatus(sessionIds, UploadSessionStatus.EXPIRED.name());
    }
}
