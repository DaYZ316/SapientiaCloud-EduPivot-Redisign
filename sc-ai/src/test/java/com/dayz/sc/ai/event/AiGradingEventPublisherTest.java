package com.dayz.sc.ai.event;

import com.dayz.sc.common.events.ai.LivePracticeAiGradingCompletedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGradingEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

    private AiGradingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        publisher = new AiGradingEventPublisher(kafkaTemplateProvider);
    }

    @Test
    void publishCompleted_shouldReturnFalseWhenKafkaSendFutureFails() {
        LivePracticeAiGradingCompletedEvent event = event();
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(eq(KafkaTopicConstants.AI_GRADING_RESULTS), eq(event.submissionId().toString()), eq(event)))
                .thenReturn(failed);

        boolean published = publisher.publishCompleted(event);

        assertThat(published).isFalse();
    }

    private LivePracticeAiGradingCompletedEvent event() {
        return new LivePracticeAiGradingCompletedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "COMPLETED",
                1,
                BigDecimal.TEN,
                "Good answer.",
                null,
                "LIVE_PRACTICE_AI_GRADING_COMPLETED",
                Instant.now(),
                "sc-ai"
        );
    }
}
