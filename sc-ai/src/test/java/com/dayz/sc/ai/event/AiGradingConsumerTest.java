package com.dayz.sc.ai.event;

import com.dayz.sc.ai.service.AiGradingService;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiGradingConsumerTest {

    @Test
    void onGradingRequested_shouldReleaseIdempotencyAndRetryWhenProcessingFails() {
        AiGradingService aiGradingService = mock(AiGradingService.class);
        KafkaIdempotencyGuard kafkaIdempotencyGuard = mock(KafkaIdempotencyGuard.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        AiGradingConsumer consumer = new AiGradingConsumer(aiGradingService, kafkaIdempotencyGuard);
        LivePracticeAiGradingRequestedEvent event = event();
        when(kafkaIdempotencyGuard.tryAcquire(eq("sc-ai"), eq(event.eventId()))).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("result publish failed"))
                .when(aiGradingService)
                .grade(event);

        assertThatThrownBy(() -> consumer.onGradingRequested(event, acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("result publish failed");

        verify(kafkaIdempotencyGuard).release("sc-ai", event.eventId());
        verify(acknowledgment, never()).acknowledge();
    }

    private LivePracticeAiGradingRequestedEvent event() {
        return new LivePracticeAiGradingRequestedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Explain encapsulation",
                "Explain encapsulation.",
                BigDecimal.TEN,
                List.of(),
                "Encapsulation hides implementation.",
                "Grade key concepts",
                "LIVE_PRACTICE_AI_GRADING_REQUESTED",
                Instant.now(),
                "sc-course"
        );
    }
}
