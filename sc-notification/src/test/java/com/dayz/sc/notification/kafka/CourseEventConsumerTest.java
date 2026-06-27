package com.dayz.sc.notification.kafka;

import com.dayz.sc.common.events.course.CourseDeletedEvent;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import com.dayz.sc.notification.repository.NotificationRepository;
import com.dayz.sc.notification.repository.NotificationTargetRepository;
import com.dayz.sc.notification.sse.NotificationSseEmitter;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CourseEventConsumerTest {

    @Test
    void onCourseEvent_shouldAckDuplicateWithoutHandling() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        KafkaIdempotencyGuard idempotencyGuard = mock(KafkaIdempotencyGuard.class);
        CourseEventConsumer consumer = new CourseEventConsumer(
                notificationRepository,
                mock(NotificationTargetRepository.class),
                mock(NotificationSseEmitter.class),
                idempotencyGuard);
        CourseDeletedEvent event = courseDeletedEvent();
        Acknowledgment ack = mock(Acknowledgment.class);
        when(idempotencyGuard.tryAcquire("sc-notification", event.eventId())).thenReturn(false);

        consumer.onCourseEvent(event, ack);

        verify(ack).acknowledge();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void onCourseEvent_shouldReleaseIdempotencyAndSkipAckWhenHandlingFails() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        KafkaIdempotencyGuard idempotencyGuard = mock(KafkaIdempotencyGuard.class);
        CourseEventConsumer consumer = new CourseEventConsumer(
                notificationRepository,
                mock(NotificationTargetRepository.class),
                mock(NotificationSseEmitter.class),
                idempotencyGuard);
        CourseDeletedEvent event = courseDeletedEvent();
        Acknowledgment ack = mock(Acknowledgment.class);
        when(idempotencyGuard.tryAcquire("sc-notification", event.eventId())).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(notificationRepository).save(any());

        assertThatThrownBy(() -> consumer.onCourseEvent(event, ack))
                .isInstanceOf(RuntimeException.class);

        verify(idempotencyGuard).release("sc-notification", event.eventId());
        verify(ack, never()).acknowledge();
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
}
