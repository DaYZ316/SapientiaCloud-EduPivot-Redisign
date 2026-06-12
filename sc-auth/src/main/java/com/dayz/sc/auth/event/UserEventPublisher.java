package com.dayz.sc.auth.event;

import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.events.user.UserDeactivatedEvent;
import com.dayz.sc.common.events.user.UserRegisteredEvent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.dayz.sc.common.util.UuidV7Generator;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    @NonNull
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegistered(User user) {
        if (user == null || user.getId() == null) {
            log.warn("Cannot publish UserRegisteredEvent: user or userId is null");
            return;
        }
        UserRegisteredEvent event = new UserRegisteredEvent(
                UuidV7Generator.generate(), user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
        kafkaTemplate.send(KafkaTopicConstants.USER_EVENTS, user.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserRegisteredEvent for user {}", user.getId(), ex);
                    } else {
                        log.debug("Published UserRegisteredEvent for user {}", user.getId());
                    }
                });
    }

    public void publishUserDeactivated(User user) {
        if (user == null || user.getId() == null) {
            log.warn("Cannot publish UserDeactivatedEvent: user or userId is null");
            return;
        }
        UserDeactivatedEvent event = new UserDeactivatedEvent(UuidV7Generator.generate(), user.getId(), user.getEmail());
        kafkaTemplate.send(KafkaTopicConstants.USER_EVENTS, user.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserDeactivatedEvent for user {}", user.getId(), ex);
                    } else {
                        log.debug("Published UserDeactivatedEvent for user {}", user.getId());
                    }
                });
    }
}
