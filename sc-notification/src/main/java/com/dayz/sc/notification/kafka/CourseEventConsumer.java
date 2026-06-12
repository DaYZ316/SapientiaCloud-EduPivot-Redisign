package com.dayz.sc.notification.kafka;

import com.dayz.sc.common.events.course.CourseCreatedEvent;
import com.dayz.sc.common.events.course.CourseDeletedEvent;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.notification.model.entity.Notification;
import com.dayz.sc.notification.model.enums.NotificationType;
import com.dayz.sc.notification.model.enums.TargetType;
import com.dayz.sc.notification.model.vo.NotificationVO;
import com.dayz.sc.notification.repository.NotificationRepository;
import com.dayz.sc.notification.sse.NotificationSseEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationSseEmitter sseEmitter;
    private final KafkaIdempotencyGuard idempotencyGuard;

    @KafkaListener(topics = "sc.course.events", groupId = "sc-notification")
    public void onCourseEvent(Object event, Acknowledgment ack) {
        try {
            if (event instanceof CourseCreatedEvent e) {
                if (!idempotencyGuard.tryAcquire("sc-notification", e.eventId())) {
                    log.info("Duplicate CourseCreatedEvent skipped: {}", e.eventId());
                    return;
                }
                handleCourseCreated(e);
            } else if (event instanceof CourseDeletedEvent e) {
                if (!idempotencyGuard.tryAcquire("sc-notification", e.eventId())) {
                    log.info("Duplicate CourseDeletedEvent skipped: {}", e.eventId());
                    return;
                }
                handleCourseDeleted(e);
            } else {
                log.warn("Unknown course event type: {}", event.getClass().getSimpleName());
            }
        } finally {
            ack.acknowledge();
        }
    }

    private void handleCourseCreated(CourseCreatedEvent event) {
        log.info("Received CourseCreatedEvent for course: {}", event.courseId());
        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.SYSTEM.getCode());
        notification.setTitle("新课程发布");
        notification.setContent("课程「" + event.title() + "」已发布");
        notification.setSenderId(event.teacherId());
        notification.setTargetType(TargetType.ALL.getCode());

        notificationRepository.save(notification);

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.broadcast(vo);
    }

    private void handleCourseDeleted(CourseDeletedEvent event) {
        log.info("Received CourseDeletedEvent for course: {}", event.courseId());
        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.SYSTEM.getCode());
        notification.setTitle("课程已删除");
        notification.setContent("课程「" + event.title() + "」已被删除");
        notification.setSenderId(event.teacherId());
        notification.setTargetType(TargetType.ALL.getCode());

        notificationRepository.save(notification);

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.broadcast(vo);
    }

    private NotificationVO toNotificationVO(Notification notification) {
        return new NotificationVO(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getSenderId(),
                notification.getTargetType(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                false,
                null
        );
    }
}
