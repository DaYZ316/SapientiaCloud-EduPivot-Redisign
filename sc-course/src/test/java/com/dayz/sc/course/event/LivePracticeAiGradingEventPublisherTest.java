package com.dayz.sc.course.event;

import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LivePracticeAiGradingEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

    private LivePracticeAiGradingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        publisher = new LivePracticeAiGradingEventPublisher(kafkaTemplateProvider);
    }

    @Test
    void publishRequested_shouldReturnFalseWhenKafkaSendFutureFails() {
        LivePracticeAiGradingRequestedEvent event = event();
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(eq(KafkaTopicConstants.AI_GRADING_REQUESTS), eq(event.submissionId().toString()), eq(event)))
                .thenReturn(failed);

        boolean published = publisher.publishRequested(event);

        assertThat(published).isFalse();
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
