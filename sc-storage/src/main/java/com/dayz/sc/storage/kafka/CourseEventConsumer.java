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
import java.util.UUID;

/**
 * 事件消费者
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {

    private static final String GROUP_ID = "sc-storage";

    private final StorageObjectMapper storageObjectMapper;
    private final MinioClient minioClient;
    private final KafkaIdempotencyGuard idempotencyGuard;

    @KafkaListener(topics = "#{T(com.dayz.sc.common.events.config.KafkaTopicConstants).COURSE_EVENTS}", groupId = "sc-storage")
    public void onCourseEvent(Object event, Acknowledgment ack) {
        try {
            if (event instanceof CourseDeletedEvent e) {
                if (!idempotencyGuard.tryAcquire(GROUP_ID, e.eventId())) {
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

        // 先逐个删除 MinIO 对象
        int cleaned = 0;
        for (StorageObject obj : objects) {
            try {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(obj.getBucket())
                        .object(obj.getObjectKey())
                        .build());
                cleaned++;
            } catch (Exception ex) {
                log.warn("Failed to delete MinIO object {}/{}: {}", obj.getBucket(), obj.getObjectKey(), ex.getMessage());
            }
        }

        // 批量更新数据库标记为已删除
        List<UUID> ids = objects.stream().map(StorageObject::getId).toList();
        Instant now = Instant.now();
        storageObjectMapper.batchUpdateDeleted(ids, StorageObject.DELETED, now);

        log.info("Cleaned up {}/{} storage objects for deleted course: {}", cleaned, objects.size(), event.courseId());
    }
}
