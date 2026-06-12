package com.dayz.sc.notification.kafka;

import com.dayz.sc.common.events.user.UserRegisteredEvent;
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
public class UserEventConsumer {

    private final NotificationRepository notificationRepository;
    private final NotificationSseEmitter sseEmitter;
    private final KafkaIdempotencyGuard idempotencyGuard;

    @KafkaListener(topics = "sc.user.events", groupId = "sc-notification")
    public void onUserEvent(Object event, Acknowledgment ack) {
        try {
            if (event instanceof UserRegisteredEvent e) {
                if (!idempotencyGuard.tryAcquire("sc-notification", e.eventId())) {
                    log.info("Duplicate UserRegisteredEvent skipped: {}", e.eventId());
                    return;
                }
                handleUserRegistered(e);
            } else {
                log.warn("Unknown user event type: {}", event.getClass().getSimpleName());
            }
        } finally {
            ack.acknowledge();
        }
    }

    private void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent for user: {}", event.userId());
        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.SYSTEM.getCode());
        notification.setTitle("欢迎加入智语·云枢");
        notification.setContent("欢迎 " + event.displayName() + " 加入智语·云枢数字孪生校园导航系统！");
        notification.setSenderId(null);
        notification.setTargetType(TargetType.USER.getCode());

        notificationRepository.save(notification);

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.sendToUser(event.userId(), vo);
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
