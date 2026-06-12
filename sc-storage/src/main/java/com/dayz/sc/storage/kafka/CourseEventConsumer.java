package com.dayz.sc.storage.kafka;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.common.events.course.CourseDeletedEvent;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import com.dayz.sc.storage.mapper.StorageObjectMapper;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.enums.StorageScopeType;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {

    private final StorageObjectMapper storageObjectMapper;
    private final MinioClient minioClient;
    private final KafkaIdempotencyGuard idempotencyGuard;

    @KafkaListener(topics = "sc.course.events", groupId = "sc-storage")
    public void onCourseEvent(Object event, Acknowledgment ack) {
        try {
            if (event instanceof CourseDeletedEvent e) {
                if (!idempotencyGuard.tryAcquire("sc-storage", e.eventId())) {
                    log.info("Duplicate CourseDeletedEvent skipped: {}", e.eventId());
                    return;
                }
                handleCourseDeleted(e);
            }
        } finally {
            ack.acknowledge();
        }
    }

    private void handleCourseDeleted(CourseDeletedEvent event) {
        log.info("Received CourseDeletedEvent for course: {}, cleaning up storage", event.courseId());

        // @TableLogic 自动过滤已删除记录，无需手动排除 DELETED 状态
        LambdaQueryWrapper<StorageObject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StorageObject::getScopeType, StorageScopeType.COURSE.name());
        wrapper.eq(StorageObject::getScopeId, event.courseId());

        List<StorageObject> objects = storageObjectMapper.selectList(wrapper);
        if (objects.isEmpty()) {
            log.info("No storage objects found for deleted course: {}", event.courseId());
            return;
        }

        int deleted = 0;
        for (StorageObject obj : objects) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(obj.getBucket())
                        .object(obj.getObjectKey())
                        .build());
            } catch (Exception ex) {
                log.warn("Failed to delete MinIO object {}/{}: {}", obj.getBucket(), obj.getObjectKey(), ex.getMessage());
            }
            obj.setDeleted(1);
            obj.setDeletedAt(Instant.now());
            storageObjectMapper.updateById(obj);
            deleted++;
        }

        log.info("Cleaned up {} storage objects for deleted course: {}", deleted, event.courseId());
    }
}
