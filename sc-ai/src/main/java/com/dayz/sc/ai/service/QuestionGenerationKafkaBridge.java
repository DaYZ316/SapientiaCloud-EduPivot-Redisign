package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.event.QuestionGenerationTaskRegistry;
import com.dayz.sc.common.events.ai.QuestionGenerationCompletedEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationProgressEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationRequestedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.util.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * QuestionGenerationKafkaBridge.
 *
 * @author DaYZ
 */
@Slf4j
@Service
public class QuestionGenerationKafkaBridge {

    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofMinutes(30);
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_TERMINATED = "terminated";

    private final ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider;
    private final QuestionGenerationCancellationService cancellationService;
    private final QuestionGenerationTaskRegistry taskRegistry;
    private final Duration responseTimeout;
    private final Map<String, Sinks.One<@NonNull QuestionGenerationCompletedEvent>> responseSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<@NonNull QuestionGenerationProgressEvent>> progressSinks = new ConcurrentHashMap<>();
    private final Set<UUID> emittedProgressEventIds = ConcurrentHashMap.newKeySet();

    @Autowired
    public QuestionGenerationKafkaBridge(
            ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider,
            QuestionGenerationCancellationService cancellationService,
            QuestionGenerationTaskRegistry taskRegistry) {
        this(kafkaTemplateProvider, cancellationService, taskRegistry, DEFAULT_RESPONSE_TIMEOUT);
    }

    QuestionGenerationKafkaBridge(
            ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider) {
        this(kafkaTemplateProvider, null, null, DEFAULT_RESPONSE_TIMEOUT);
    }

    QuestionGenerationKafkaBridge(
            ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider,
            Duration responseTimeout) {
        this(kafkaTemplateProvider, null, null, responseTimeout);
    }

    QuestionGenerationKafkaBridge(
            ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider,
            QuestionGenerationCancellationService cancellationService,
            QuestionGenerationTaskRegistry taskRegistry,
            Duration responseTimeout) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.cancellationService = cancellationService;
        this.taskRegistry = taskRegistry;
        this.responseTimeout = responseTimeout;
    }

    public Mono<@NonNull AiAgentResult> submit(ChatRequest request,
                                               UUID conversationId,
                                               UUID userId,
                                               Integer role,
                                               AiAgentMode mode,
                                               String requestId,
                                               UUID assistantMessageId) {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return Mono.empty();
        }
        String finalRequestId = hasText(requestId) ? requestId : UuidV7Generator.generate().toString();
        Sinks.One<@NonNull QuestionGenerationCompletedEvent> responseSink = Sinks.one();
        responseSinks.put(finalRequestId, responseSink);
        progressSinks.computeIfAbsent(finalRequestId, ignored -> Sinks.many().replay().limit(256));
        QuestionGenerationRequestedEvent event = new QuestionGenerationRequestedEvent(
                UuidV7Generator.generate(),
                finalRequestId,
                conversationId,
                assistantMessageId,
                userId,
                role,
                request.courseId(),
                request.message(),
                mode.name(),
                generationPayload(request.generation()));
        CompletableFuture<?> sendFuture;
        try {
            sendFuture = kafkaTemplate.send(KafkaTopicConstants.QUESTION_GENERATION_REQUESTS, finalRequestId, event);
        } catch (RuntimeException exception) {
            cleanup(finalRequestId);
            return Mono.empty();
        }
        Mono<@NonNull QuestionGenerationCompletedEvent> responseMono = responseSink.asMono().cache();
        return Mono.fromFuture(sendFuture)
                .onErrorResume(error -> {
                    log.warn("Failed to publish question generation request requestId={}", finalRequestId, error);
                    cleanup(finalRequestId);
                    return Mono.empty();
                })
                .flatMap(ignored -> responseMono.timeout(responseTimeout))
                .flatMap(response -> {
                    if (STATUS_COMPLETED.equals(response.status())) {
                        return Mono.just(new AiAgentResult(
                                response.content(),
                                AiMessageType.valueOf(response.messageType()),
                                response.payload()));
                    }
                    if (STATUS_TERMINATED.equals(response.status())) {
                        return Mono.error(new GenerationCancelledException(finalRequestId));
                    }
                    return Mono.error(new IllegalStateException(hasText(response.errorMessage())
                            ? response.errorMessage()
                            : "Question generation worker failed"));
                })
                .doFinally(signalType -> cleanup(finalRequestId))
                .onErrorResume(error -> {
                    log.warn("Question generation Kafka response failed requestId={}", finalRequestId, error);
                    cleanup(finalRequestId);
                    return Mono.error(error);
                });
    }

    public Flux<@NonNull QuestionGenerationProgressEvent> progress(String requestId) {
        if (!hasText(requestId)) {
            return Flux.empty();
        }
        Sinks.Many<@NonNull QuestionGenerationProgressEvent> sink = progressSinks.computeIfAbsent(
                requestId,
                ignored -> Sinks.many().replay().limit(256));
        return sink.asFlux();
    }

    public void publishStage(String requestId, GenerationStageEvent event) {
        publishProgress(requestId, "generation_stage", event);
    }

    public void publishAgentSearch(String requestId, AgentSearchEvent event) {
        publishProgress(requestId, "agent_search", event);
    }

    public void publishResponse(String requestId, AiAgentResult result) {
        publishCompleted(QuestionGenerationCompletedEvent.completed(
                requestId,
                result.content(),
                result.messageType().name(),
                result.payload()));
    }

    public void publishFailure(String requestId, String errorMessage) {
        publishCompleted(QuestionGenerationCompletedEvent.failed(requestId, errorMessage));
    }

    public void cancel(String requestId, AiAgentMode mode) {
        if (!hasText(requestId)) {
            return;
        }
        if (cancellationService != null) {
            cancellationService.cancel(requestId);
        }
        if (taskRegistry != null) {
            taskRegistry.cancel(requestId);
        }
        GenerationStageEvent stage = GenerationStageEvent.of(
                requestId,
                mode == null ? AiAgentMode.QUESTION.name() : mode.name(),
                "TERMINATED",
                "terminated",
                "任务已终止",
                "用户已终止本次生成任务。",
                Map.of());
        emitProgressLocally(new QuestionGenerationProgressEvent(
                UuidV7Generator.generate(),
                requestId,
                "generation_stage",
                objectPayload(stage),
                Instant.now()));
        Sinks.One<@NonNull QuestionGenerationCompletedEvent> responseSink = responseSinks.get(requestId);
        if (responseSink != null) {
            responseSink.tryEmitValue(QuestionGenerationCompletedEvent.terminated(requestId));
        }
        cleanup(requestId);
    }

    @KafkaListener(topics = KafkaTopicConstants.QUESTION_GENERATION_PROGRESS, groupId = "sc-ai-question-generation-progress")
    public void onProgress(QuestionGenerationProgressEvent event, Acknowledgment acknowledgment) {
        try {
            emitProgressLocally(event);
        } finally {
            acknowledge(acknowledgment);
        }
    }

    @KafkaListener(topics = KafkaTopicConstants.QUESTION_GENERATION_RESPONSES, groupId = "sc-ai-question-generation-response")
    public void onCompleted(QuestionGenerationCompletedEvent event, Acknowledgment acknowledgment) {
        try {
            if (event != null && hasText(event.requestId())) {
                Sinks.One<@NonNull QuestionGenerationCompletedEvent> sink = responseSinks.get(event.requestId());
                if (sink != null) {
                    sink.tryEmitValue(event);
                }
                completeProgress(event.requestId());
            }
        } finally {
            acknowledge(acknowledgment);
        }
    }

    private void publishProgress(String requestId, String eventType, Object payload) {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null || !hasText(requestId) || payload == null) {
            return;
        }
        QuestionGenerationProgressEvent event = new QuestionGenerationProgressEvent(
                UuidV7Generator.generate(),
                requestId,
                eventType,
                objectPayload(payload),
                Instant.now());
        emitProgressLocally(event);
        kafkaTemplate.send(KafkaTopicConstants.QUESTION_GENERATION_PROGRESS, requestId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish question generation progress requestId={}", requestId, ex);
                    }
                });
    }

    private void publishCompleted(QuestionGenerationCompletedEvent event) {
        KafkaTemplate<@NonNull String, @NonNull Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null || event == null || !hasText(event.requestId())) {
            return;
        }
        kafkaTemplate.send(KafkaTopicConstants.QUESTION_GENERATION_RESPONSES, event.requestId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish question generation response requestId={}", event.requestId(), ex);
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectPayload(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", payload);
        return result;
    }

    private Map<String, Object> generationPayload(GenerationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request == null) {
            return payload;
        }
        payload.put("questionBankId", request.questionBankId() == null ? null : request.questionBankId().toString());
        payload.put("questionCount", request.questionCount());
        payload.put("questionType", request.questionType());
        payload.put("difficulty", request.difficulty());
        payload.put("scorePerQuestion", request.scorePerQuestion());
        payload.put("totalScore", request.totalScore());
        payload.put("totalEstimatedTime", request.totalEstimatedTime());
        payload.put("paperName", request.paperName());
        payload.put("paperType", request.paperType());
        payload.put("requirement", request.requirement());
        payload.put("chapterIds", request.chapterIds());
        payload.put("knowledgePoints", request.knowledgePoints());
        payload.put("abilityGoals", request.abilityGoals());
        return payload;
    }

    private void cleanup(String requestId) {
        responseSinks.remove(requestId);
        Sinks.Many<@NonNull QuestionGenerationProgressEvent> progressSink = progressSinks.remove(requestId);
        if (progressSink != null) {
            progressSink.tryEmitComplete();
        }
    }

    private void emitProgressLocally(QuestionGenerationProgressEvent event) {
        if (event == null || !hasText(event.requestId())) {
            return;
        }
        UUID eventId = event.eventId();
        if (eventId != null && !emittedProgressEventIds.add(eventId)) {
            return;
        }
        Sinks.Many<@NonNull QuestionGenerationProgressEvent> sink = progressSinks.get(event.requestId());
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }

    private void completeProgress(String requestId) {
        Sinks.Many<@NonNull QuestionGenerationProgressEvent> progressSink = progressSinks.get(requestId);
        if (progressSink != null) {
            progressSink.tryEmitComplete();
        }
    }

    private void acknowledge(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
