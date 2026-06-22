package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationCompletedEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationProgressEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationRequestedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.util.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class QuestionGenerationKafkaBridge {

    private static final Duration DEFAULT_DISPATCH_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofMinutes(30);

    private final ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider;
    private final Duration dispatchTimeout;
    private final Duration responseTimeout;
    private final Map<String, Sinks.One<QuestionGenerationCompletedEvent>> responseSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<QuestionGenerationProgressEvent>> progressSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.One<Void>> startedSinks = new ConcurrentHashMap<>();
    private final Set<UUID> emittedProgressEventIds = ConcurrentHashMap.newKeySet();

    @Autowired
    public QuestionGenerationKafkaBridge(
            ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider) {
        this(kafkaTemplateProvider, DEFAULT_DISPATCH_TIMEOUT, DEFAULT_RESPONSE_TIMEOUT);
    }

    QuestionGenerationKafkaBridge(
            ObjectProvider<@NonNull KafkaTemplate<@NonNull String, @NonNull Object>> kafkaTemplateProvider,
            Duration dispatchTimeout,
            Duration responseTimeout) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.dispatchTimeout = dispatchTimeout;
        this.responseTimeout = responseTimeout;
    }

    public Mono<AiAgentResult> submit(ChatRequest request,
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
        AtomicBoolean workerStarted = new AtomicBoolean(false);
        Sinks.One<QuestionGenerationCompletedEvent> responseSink = Sinks.one();
        responseSinks.put(finalRequestId, responseSink);
        Sinks.One<Void> startedSink = Sinks.one();
        startedSinks.put(finalRequestId, startedSink);
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
        try {
            kafkaTemplate.send(KafkaTopicConstants.QUESTION_GENERATION_REQUESTS, finalRequestId, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("Failed to publish question generation request requestId={}", finalRequestId, ex);
                            responseSink.tryEmitEmpty();
                            cleanup(finalRequestId);
                        }
                    });
        } catch (RuntimeException exception) {
            cleanup(finalRequestId);
            return Mono.empty();
        }
        Mono<QuestionGenerationCompletedEvent> responseMono = responseSink.asMono().cache();
        return Mono.firstWithSignal(startedSink.asMono(), responseMono.then())
                .doOnSuccess(ignored -> workerStarted.set(true))
                .timeout(dispatchTimeout)
                .then(responseMono.timeout(responseTimeout))
                .flatMap(response -> {
                    if ("completed".equals(response.status())) {
                        return Mono.just(new AiAgentResult(
                                response.content(),
                                AiMessageType.valueOf(response.messageType()),
                                response.payload()));
                    }
                    return Mono.error(new IllegalStateException(hasText(response.errorMessage())
                            ? response.errorMessage()
                            : "Question generation worker failed"));
                })
                .doFinally(signalType -> cleanup(finalRequestId))
                .onErrorResume(error -> {
                    if (error instanceof TimeoutException && !workerStarted.get()) {
                        log.warn("Question generation Kafka dispatch timed out requestId={} timeoutMs={}",
                                finalRequestId, dispatchTimeout.toMillis());
                        cleanup(finalRequestId);
                        return Mono.empty();
                    } else {
                        log.warn("Question generation Kafka response failed requestId={}", finalRequestId, error);
                    }
                    cleanup(finalRequestId);
                    return Mono.error(error);
                });
    }

    public Flux<QuestionGenerationProgressEvent> progress(String requestId) {
        if (!hasText(requestId)) {
            return Flux.empty();
        }
        Sinks.Many<QuestionGenerationProgressEvent> sink = progressSinks.computeIfAbsent(
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
                Sinks.One<Void> startedSink = startedSinks.get(event.requestId());
                if (startedSink != null) {
                    startedSink.tryEmitEmpty();
                }
                Sinks.One<QuestionGenerationCompletedEvent> sink = responseSinks.get(event.requestId());
                if (sink != null) {
                    sink.tryEmitValue(event);
                }
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
        Sinks.One<Void> startedSink = startedSinks.remove(requestId);
        if (startedSink != null) {
            startedSink.tryEmitEmpty();
        }
        Sinks.Many<QuestionGenerationProgressEvent> progressSink = progressSinks.remove(requestId);
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
        Sinks.One<Void> startedSink = startedSinks.get(event.requestId());
        if (startedSink != null) {
            startedSink.tryEmitEmpty();
        }
        Sinks.Many<QuestionGenerationProgressEvent> sink = progressSinks.get(event.requestId());
        if (sink != null) {
            sink.tryEmitNext(event);
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
