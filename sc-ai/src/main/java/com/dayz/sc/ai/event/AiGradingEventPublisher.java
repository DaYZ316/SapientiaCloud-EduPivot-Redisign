package com.dayz.sc.ai.event;

import com.dayz.sc.common.events.ai.LivePracticeAiGradingCompletedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiGradingEventPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider;

    public boolean publishCompleted(LivePracticeAiGradingCompletedEvent event) {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Skip LivePracticeAiGradingCompletedEvent for submission {}: KafkaTemplate is unavailable",
                    event.submissionId());
            return false;
        }
        try {
            kafkaTemplate.send(KafkaTopicConstants.AI_GRADING_RESULTS, event.submissionId().toString(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published LivePracticeAiGradingCompletedEvent for submission {}", event.submissionId());
            return true;
        } catch (Exception exception) {
            log.error("Failed to publish LivePracticeAiGradingCompletedEvent for submission {}",
                    event.submissionId(), exception);
            return false;
        }
    }
}
