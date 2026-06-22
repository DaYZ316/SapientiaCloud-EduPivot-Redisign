package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationCompletedEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationProgressEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionGenerationKafkaBridgeTest {

    @Test
    void submitShouldReturnEmptyWhenKafkaTemplateUnavailable() {
        QuestionGenerationKafkaBridge bridge = new QuestionGenerationKafkaBridge(emptyProvider());

        AiAgentResult result = bridge.submit(
                        new ChatRequest(UUID.randomUUID(), "生成题目", "QUESTION", null, null),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        AiAgentMode.QUESTION,
                        "request-1")
                .block();

        assertThat(result).isNull();
    }

    @Test
    void publishStageShouldSendProgressWhenKafkaTemplateAvailable() {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplate();
        QuestionGenerationKafkaBridge bridge = new QuestionGenerationKafkaBridge(provider(kafkaTemplate));
        GenerationStageEvent event = GenerationStageEvent.of(
                "request-1", "QUESTION", "PLANNED", "processing", "规划完成", "summary", Map.of());

        bridge.publishStage("request-1", event);

        verify(kafkaTemplate).send(
                eq(KafkaTopicConstants.QUESTION_GENERATION_PROGRESS),
                eq("request-1"),
                any(QuestionGenerationProgressEvent.class));
    }

    @Test
    void progressShouldReplayConsumedProgressForRequest() {
        QuestionGenerationKafkaBridge bridge = new QuestionGenerationKafkaBridge(emptyProvider());
        List<QuestionGenerationProgressEvent> received = new CopyOnWriteArrayList<>();
        bridge.progress("request-1").subscribe(received::add);
        QuestionGenerationProgressEvent event = new QuestionGenerationProgressEvent(
                UUID.randomUUID(),
                "request-1",
                "generation_stage",
                Map.of("stage", "PLANNED"),
                Instant.now());

        bridge.onProgress(event, null);

        assertThat(received).containsExactly(event);
    }

    @Test
    void submitShouldKeepExistingProgressSink() {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplate();
        QuestionGenerationKafkaBridge bridge = new QuestionGenerationKafkaBridge(provider(kafkaTemplate));
        List<QuestionGenerationProgressEvent> received = new CopyOnWriteArrayList<>();
        String requestId = "request-1";
        bridge.progress(requestId).subscribe(received::add);

        bridge.submit(
                new ChatRequest(UUID.randomUUID(), "generate questions", "QUESTION", null, null),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                AiAgentMode.QUESTION,
                requestId);
        QuestionGenerationProgressEvent event = new QuestionGenerationProgressEvent(
                UUID.randomUUID(),
                requestId,
                "generation_stage",
                Map.of("stage", "PLANNED"),
                Instant.now());
        bridge.onProgress(event, null);

        assertThat(received).containsExactly(event);
    }

    @Test
    void submitShouldCompleteWhenResponseArrives() {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplate();
        QuestionGenerationKafkaBridge bridge = new QuestionGenerationKafkaBridge(provider(kafkaTemplate));
        String requestId = "request-1";

        var resultMono = bridge.submit(
                new ChatRequest(UUID.randomUUID(), "生成题目", "QUESTION", null,
                        new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.TEN,
                                null, null, null, null, null, null, null, null)),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                AiAgentMode.QUESTION,
                requestId);
        bridge.onCompleted(QuestionGenerationCompletedEvent.completed(
                requestId,
                "完成",
                AiMessageType.QUESTION_SET.name(),
                Map.of("questions", List.of())), null);

        AiAgentResult result = resultMono.block();

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("完成");
        assertThat(result.messageType()).isEqualTo(AiMessageType.QUESTION_SET);
        verify(kafkaTemplate).send(
                eq(KafkaTopicConstants.QUESTION_GENERATION_REQUESTS),
                eq(requestId),
                any());
    }

    @Test
    void submitShouldPropagateWorkerFailureWhenResponseArrives() {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplate();
        QuestionGenerationKafkaBridge bridge = new QuestionGenerationKafkaBridge(provider(kafkaTemplate));
        String requestId = "request-1";

        var resultMono = bridge.submit(
                new ChatRequest(UUID.randomUUID(), "generate questions", "QUESTION", null,
                        new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.TEN,
                                null, null, null, null, null, null, null, null)),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                AiAgentMode.QUESTION,
                requestId);
        bridge.onCompleted(QuestionGenerationCompletedEvent.failed(requestId, "worker failed"), null);

        assertThatThrownBy(resultMono::block)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("worker failed");
    }

    @Test
    void submitShouldReturnEmptyWhenWorkerDoesNotStart() {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplate();
        QuestionGenerationKafkaBridge bridge = new QuestionGenerationKafkaBridge(
                provider(kafkaTemplate),
                Duration.ofMillis(20),
                Duration.ofSeconds(1));

        AiAgentResult result = bridge.submit(
                        new ChatRequest(UUID.randomUUID(), "generate questions", "QUESTION", null, null),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        AiAgentMode.QUESTION,
                        "request-1")
                .block(Duration.ofSeconds(1));

        assertThat(result).isNull();
        verify(kafkaTemplate).send(
                eq(KafkaTopicConstants.QUESTION_GENERATION_REQUESTS),
                eq("request-1"),
                any());
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate() {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        return kafkaTemplate;
    }

    private ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> provider(
            KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate) {
        return new ObjectProvider<>() {
            @Override
            public KafkaTemplate<@NonNull String, @NonNull Object> getObject(Object... args) {
                return kafkaTemplate;
            }

            @Override
            public KafkaTemplate<@NonNull String, @NonNull Object> getIfAvailable() {
                return kafkaTemplate;
            }

            @Override
            public KafkaTemplate<@NonNull String, @NonNull Object> getObject() {
                return kafkaTemplate;
            }
        };
    }

    private ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> emptyProvider() {
        return provider(null);
    }
}
