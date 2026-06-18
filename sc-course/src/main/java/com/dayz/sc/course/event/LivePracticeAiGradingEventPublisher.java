package com.dayz.sc.course.event;

import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LivePracticeAiGradingEventPublisher {

    private final ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider;

    public void publishRequested(LivePracticeAiGradingRequestedEvent event) {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Skip LivePracticeAiGradingRequestedEvent for submission {}: KafkaTemplate is unavailable",
                    event.submissionId());
            return;
        }
        kafkaTemplate.send(KafkaTopicConstants.AI_GRADING_REQUESTS, event.submissionId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish LivePracticeAiGradingRequestedEvent for submission {}",
                                event.submissionId(), ex);
                    } else {
                        log.debug("Published LivePracticeAiGradingRequestedEvent for submission {}", event.submissionId());
                    }
                });
    }
}
