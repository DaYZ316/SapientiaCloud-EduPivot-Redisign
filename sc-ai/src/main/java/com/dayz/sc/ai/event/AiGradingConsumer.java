package com.dayz.sc.ai.event;

import com.dayz.sc.ai.service.AiGradingService;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiGradingConsumer {

    private static final String GROUP_ID = "sc-ai";

    private final AiGradingService aiGradingService;
    private final KafkaIdempotencyGuard kafkaIdempotencyGuard;

    @KafkaListener(topics = KafkaTopicConstants.AI_GRADING_REQUESTS, groupId = GROUP_ID)
    public void onGradingRequested(LivePracticeAiGradingRequestedEvent event, Acknowledgment acknowledgment) {
        if (event == null || event.eventId() == null) {
            acknowledgment.acknowledge();
            return;
        }
        if (!kafkaIdempotencyGuard.tryAcquire(GROUP_ID, event.eventId())) {
            acknowledgment.acknowledge();
            return;
        }

        aiGradingService.grade(event);
        acknowledgment.acknowledge();
    }
}
