package com.dayz.sc.auth.event;

import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.events.user.UserDeactivatedEvent;
import com.dayz.sc.common.events.user.UserRegisteredEvent;
import java.time.Instant;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.dayz.sc.common.util.UuidV7Generator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户事件发布者，负责发布用户注册和停用事件。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {

    @NonNull
    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

    public void publishUserRegistered(User user) {
        if (user == null || user.getId() == null) {
            log.warn("Cannot publish UserRegisteredEvent: user or userId is null");
            return;
        }
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate not available, skipping event publishing");
            return;
        }
        UserRegisteredEvent event = new UserRegisteredEvent(
                UuidV7Generator.generate(), user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                "USER_REGISTERED", Instant.now(), "sc-auth");
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
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate not available, skipping event publishing");
            return;
        }
        UserDeactivatedEvent event = new UserDeactivatedEvent(UuidV7Generator.generate(), user.getId(), user.getEmail(),
                "USER_DEACTIVATED", Instant.now(), "sc-auth");
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
