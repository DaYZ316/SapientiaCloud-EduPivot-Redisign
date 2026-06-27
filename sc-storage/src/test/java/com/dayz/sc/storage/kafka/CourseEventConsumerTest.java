package com.dayz.sc.storage.kafka;

import com.dayz.sc.common.events.course.CourseDeletedEvent;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import com.dayz.sc.storage.mapper.StorageObjectMapper;
import com.dayz.sc.storage.model.entity.StorageObject;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class CourseEventConsumerTest {

    @Test
    void onCourseEvent_shouldAckDuplicateWithoutCleanup() {
        StorageObjectMapper storageObjectMapper = mock(StorageObjectMapper.class);
        KafkaIdempotencyGuard idempotencyGuard = mock(KafkaIdempotencyGuard.class);
        CourseEventConsumer consumer = new CourseEventConsumer(
                storageObjectMapper,
                mock(MinioClient.class),
                idempotencyGuard);
        CourseDeletedEvent event = courseDeletedEvent();
        Acknowledgment ack = mock(Acknowledgment.class);
        when(idempotencyGuard.tryAcquire("sc-storage", event.eventId())).thenReturn(false);

        consumer.onCourseEvent(event, ack);

        verify(ack).acknowledge();
        verify(storageObjectMapper, never()).selectList(any());
    }

    @Test
    void onCourseEvent_shouldReleaseIdempotencyAndSkipAckWhenMinioDeleteFails() throws Exception {
        StorageObjectMapper storageObjectMapper = mock(StorageObjectMapper.class);
        MinioClient minioClient = mock(MinioClient.class);
        KafkaIdempotencyGuard idempotencyGuard = mock(KafkaIdempotencyGuard.class);
        CourseEventConsumer consumer = new CourseEventConsumer(
                storageObjectMapper,
                minioClient,
                idempotencyGuard);
        CourseDeletedEvent event = courseDeletedEvent();
        Acknowledgment ack = mock(Acknowledgment.class);
        when(idempotencyGuard.tryAcquire("sc-storage", event.eventId())).thenReturn(true);
        when(storageObjectMapper.selectList(any())).thenReturn(List.of(storageObject()));
        org.mockito.Mockito.doThrow(new RuntimeException("minio down"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> consumer.onCourseEvent(event, ack))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyGuard).release("sc-storage", event.eventId());
        verify(ack, never()).acknowledge();
        verify(storageObjectMapper, never()).batchUpdateDeleted(any(), anyInt(), any());
    }

    private CourseDeletedEvent courseDeletedEvent() {
        return new CourseDeletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Course",
                UUID.randomUUID(),
                "COURSE_DELETED",
                Instant.now(),
                "sc-course");
    }

    private StorageObject storageObject() {
        StorageObject object = new StorageObject();
        object.setId(UUID.randomUUID());
        object.setBucket("course-files");
        object.setObjectKey("course/file.pdf");
        return object;
    }
}
