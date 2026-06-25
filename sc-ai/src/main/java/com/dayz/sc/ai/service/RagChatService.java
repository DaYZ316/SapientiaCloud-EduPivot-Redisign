package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.enums.MessageRole;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.ChatMessageVO;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.common.events.ai.QuestionGenerationProgressEvent;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.model.UserRole;
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.errors.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RagChatService {

    private static final int MAX_TITLE_LENGTH = 60;
    private static final Duration RAG_RETRIEVAL_TIMEOUT = Duration.ofMillis(1200);
    private static final Duration TITLE_GENERATION_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration GENERATION_KEEPALIVE_INTERVAL = Duration.ofSeconds(15);
    private static final List<String> TIME_SENSITIVE_TERMS = List.of(
            "今天", "现在", "最新", "近期", "今年", "本周", "本月", "新闻",
            "当前日期", "当前时间", "today", "now", "latest", "recent", "news",
            "this year", "this week", "this month", "current date", "current time");
    private static final String PAPER_MEMORY_LABEL = "\u4e0a\u4e00\u4efd AI \u51fa\u5377\u7ed3\u679c";
    private static final String QUESTION_SET_MEMORY_LABEL = "\u4e0a\u4e00\u7ec4 AI \u51fa\u9898\u7ed3\u679c";
    private static final String NO_RAG_CONTEXT =
            "暂时没有找到与你的问题直接相关的课程资料。";
    private static final String RAG_RETRIEVAL_UNAVAILABLE =
            "课程资料暂时无法读取。";
    private static final String AI_PROVIDER_NOT_FOUND_MESSAGE =
            "AI 服务暂时不可用，请稍后再试或联系管理员。";

    private final ChatClient chatClient;
    private final ObjectProvider<@NonNull VectorStore> vectorStoreProvider;
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final AiProperties aiProperties;
    private final AiAgentService aiAgentService;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final PlatformDataTool platformDataTool;
    private final ChatVectorMemoryService chatVectorMemoryService;
    private final AgentSearchTools agentSearchTools;
    private final AiProviderCallGuard aiProviderCallGuard;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<@NonNull QuestionGenerationKafkaBridge> questionGenerationKafkaBridgeProvider;
    private final GenerationMessageStateService generationMessageStateService;

    public RagChatService(ChatClient chatClient,
                          ObjectProvider<@NonNull VectorStore> vectorStoreProvider,
                          MessageRepository messageRepository,
                          ConversationService conversationService,
                          AiProperties aiProperties,
                          AiAgentService aiAgentService,
                          AiRuntimeGuard aiRuntimeGuard,
                          PlatformDataTool platformDataTool,
                          ChatVectorMemoryService chatVectorMemoryService,
                          AgentSearchTools agentSearchTools,
                          AiProviderCallGuard aiProviderCallGuard,
                          ObjectMapper objectMapper,
                          ObjectProvider<@NonNull QuestionGenerationKafkaBridge> questionGenerationKafkaBridgeProvider,
                          GenerationMessageStateService generationMessageStateService) {
        this.chatClient = chatClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
        this.aiProperties = aiProperties;
        this.aiAgentService = aiAgentService;
        this.aiRuntimeGuard = aiRuntimeGuard;
        this.platformDataTool = platformDataTool;
        this.chatVectorMemoryService = chatVectorMemoryService;
        this.agentSearchTools = agentSearchTools;
        this.aiProviderCallGuard = aiProviderCallGuard;
        this.objectMapper = objectMapper;
        this.questionGenerationKafkaBridgeProvider = questionGenerationKafkaBridgeProvider;
        this.generationMessageStateService = generationMessageStateService;
    }

    public Flux<@NonNull ServerSentEvent<String>> stream(ChatRequest request,
                                                         UUID userId,
                                                         Integer role,
                                                         String authorization) {
        return Flux.defer(() -> {
            long startedAtNanos = System.nanoTime();
            log.info("AI chat stream request received userId={} conversationId={} elapsedMs={}",
                    userId, request.conversationId(), elapsedMs(startedAtNanos));
            AiAgentMode mode = AiAgentMode.resolve(request.agentMode(), request.message());
            if (mode == AiAgentMode.CHAT) {
                aiRuntimeGuard.requireConfigured();
            }

            UUID conversationId = request.conversationId();
            String initialTitle = null;
            boolean newConversation = false;
            if (conversationId == null) {
                initialTitle = fallbackTitle(request.message());
                conversationId = conversationService.createConversation(initialTitle, userId);
                newConversation = true;
                log.info("AI chat conversation created conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos));
            }

            UUID streamConversationId = conversationId;
            ChatRequest streamRequest = requestWithConversationId(request, streamConversationId);
            Flux<@NonNull ServerSentEvent<String>> chunks = streamContent(
                    streamRequest, userId, role, authorization, streamConversationId, mode, startedAtNanos);
            if (!newConversation) {
                return chunks;
            }

            Mono<@NonNull ServerSentEvent<String>> titleUpdate = asyncTitleUpdate(
                    streamConversationId,
                    userId,
                    request.message(),
                    titleContext(streamRequest, mode),
                    initialTitle,
                    startedAtNanos);
            return Flux.just(conversationEvent(streamConversationId, initialTitle))
                    .concatWith(chunks)
                    .concatWith(titleUpdate.flux());
        }).onErrorResume(error -> Flux.just(errorEvent(error)));
    }

    public Flux<@NonNull ServerSentEvent<String>> streamGenerationProgress(UUID conversationId,
                                                                            UUID messageId,
                                                                            UUID userId) {
        return Flux.defer(() -> {
            conversationService.requireOwnedConversation(conversationId, userId);
            ChatMessage message = generationMessage(conversationId, messageId);
            Map<String, Object> payload = message.getPayload() == null ? Map.of() : message.getPayload();
            ServerSentEvent<String> snapshot = generationSnapshotEvent(message);
            String requestId = textValue(payload.get("generationRequestId"));
            QuestionGenerationKafkaBridge kafkaBridge = questionGenerationKafkaBridgeProvider.getIfAvailable();
            if (!isProcessingGeneration(payload) || !StringUtils.hasText(requestId) || kafkaBridge == null) {
                return Flux.just(snapshot);
            }
            return Flux.just(snapshot).concatWith(liveGenerationProgress(kafkaBridge, requestId, messageId));
        }).onErrorResume(error -> Flux.just(errorEvent(error)));
    }

    public void terminateGeneration(UUID conversationId, UUID messageId, UUID userId) {
        conversationService.requireOwnedConversation(conversationId, userId);
        ChatMessage message = generationMessage(conversationId, messageId);
        Map<String, Object> payload = message.getPayload() == null ? Map.of() : message.getPayload();
        String requestId = textValue(payload.get("generationRequestId"));
        AiAgentMode mode = generationMode(message, payload);
        generationMessageStateService.markTerminated(
                messageId,
                requestId,
                mode,
                "用户已终止本次生成任务。");
        QuestionGenerationKafkaBridge kafkaBridge = questionGenerationKafkaBridgeProvider.getIfAvailable();
        if (kafkaBridge != null && StringUtils.hasText(requestId)) {
            kafkaBridge.cancel(requestId, mode);
        }
    }

    private ChatRequest requestWithConversationId(ChatRequest request, UUID conversationId) {
        if (Objects.equals(request.conversationId(), conversationId)) {
            return request;
        }
        return new ChatRequest(
                conversationId,
                request.message(),
                request.agentMode(),
                request.courseId(),
                request.generation());
    }

    private Flux<@NonNull ServerSentEvent<String>> streamContent(ChatRequest request,
                                                                 UUID userId,
                                                                 Integer role,
                                                                 String authorization,
                                                                 UUID conversationId,
                                                                 AiAgentMode mode,
                                                                 long startedAtNanos) {
        if (mode == AiAgentMode.CHAT && request.courseId() == null) {
            return streamRagChat(conversationId, userId, role, authorization, request.message(), startedAtNanos);
        }
        if (mode == AiAgentMode.CHAT) {
            return streamCourseChat(conversationId, userId, role, authorization, request, startedAtNanos);
        }

        ChatMessage currentUserMessage = persistUserMessage(
                conversationId,
                userId,
                request.message(),
                generationRequestPayload(mode, request.generation()));
        return streamGenerationAgent(conversationId, userId, role, request, mode, currentUserMessage, startedAtNanos);
    }

    private Flux<@NonNull ServerSentEvent<String>> streamGenerationAgent(UUID conversationId,
                                                                         UUID userId,
                                                                         Integer role,
                                                                         ChatRequest request,
                                                                         AiAgentMode mode,
                                                                         ChatMessage currentUserMessage,
                                                                         long startedAtNanos) {
        Sinks.Many<GenerationStageEvent> stageSink = Sinks.many().unicast().onBackpressureBuffer();
        Sinks.Many<AgentSearchEvent> agentSearchSink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        String requestId = UuidV7Generator.generate().toString();
        ChatMessage assistantMessage = generationMessageStateService.createProcessingMessage(
                conversationId,
                mode,
                requestId,
                request.generation());
        Flux<@NonNull ServerSentEvent<String>> stageEvents = stageSink.asFlux()
                .map(event -> generationStageEvent(assistantMessage.getId(), event));
        Flux<@NonNull ServerSentEvent<String>> agentSearchEvents = agentSearchSink.asFlux()
                .map(this::agentSearchEvent);
        QuestionGenerationKafkaBridge kafkaBridge = questionGenerationKafkaBridgeProvider.getIfAvailable();
        Flux<@NonNull ServerSentEvent<String>> kafkaProgressEvents = kafkaBridge == null
                ? Flux.empty()
                : kafkaBridge.progress(requestId)
                .map(event -> generationProgressEvent(event, assistantMessage.getId()));
        Mono<AiAgentResult> kafkaResult = kafkaBridge == null
                ? Mono.empty()
                : kafkaBridge.submit(request, conversationId, userId, role, mode, requestId, assistantMessage.getId());
        Mono<AiAgentResult> resultMono = kafkaResult
                .switchIfEmpty(Mono.defer(() -> cancelled.get() ? Mono.<AiAgentResult>empty() : Mono.fromCallable(() -> {
                    log.info("AI chat agent run started conversationId={} mode={} elapsedMs={}",
                            conversationId, mode, elapsedMs(startedAtNanos));
                    AiAgentResult generatedResult = aiAgentService.runGeneration(
                            request,
                            userId,
                             role,
                             event -> {
                                if (cancelled.get()) {
                                    return;
                                }
                                 generationMessageStateService.appendStage(assistantMessage.getId(), event);
                                 stageSink.tryEmitNext(event);
                             },
                             event -> {
                                if (!cancelled.get()) {
                                    agentSearchSink.tryEmitNext(event);
                                }
                             },
                             requestId);
                     return generatedResult;
                 }).subscribeOn(Schedulers.boundedElastic())))
                .doOnError(error -> generationMessageStateService.markFailed(
                        assistantMessage.getId(),
                        requestId,
                        mode,
                        "题目生成失败，请稍后重试。"))
                .cache();
         Flux<@NonNull ServerSentEvent<String>> resultEvents = resultMono
                 .flatMapMany(result -> {
                    if (cancelled.get()) {
                        return Flux.empty();
                    }
                     generationMessageStateService.markCompleted(assistantMessage.getId(), requestId, mode, result);
                     log.info("AI chat agent run completed conversationId={} mode={} elapsedMs={}",
                             conversationId, mode, elapsedMs(startedAtNanos));
                    return Flux.just(
                            generationResultEvent(assistantMessage.getId(), requestId, mode, result),
                            chunkEvent(result.content()));
                })
                .doFinally(signalType -> {
                    stageSink.tryEmitComplete();
                    agentSearchSink.tryEmitComplete();
                });
        Flux<@NonNull ServerSentEvent<String>> keepaliveEvents = Flux.interval(GENERATION_KEEPALIVE_INTERVAL)
                .map(ignored -> keepaliveEvent())
                .takeUntilOther(resultMono.then());
        return Flux.merge(stageEvents, agentSearchEvents, kafkaProgressEvents, keepaliveEvents, resultEvents)
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL && cancelled.compareAndSet(false, true)) {
                        generationMessageStateService.markTerminated(
                                assistantMessage.getId(),
                                requestId,
                                mode,
                                "用户已终止本次生成任务。");
                        if (kafkaBridge != null) {
                            kafkaBridge.cancel(requestId, mode);
                        }
                    }
                });
    }

    private Flux<@NonNull ServerSentEvent<String>> streamRagChat(UUID conversationId,
                                                                 UUID userId,
                                                                 Integer role,
                                                                 String authorization,
                                                                 String question,
                                                                 long startedAtNanos) {
        ChatMessage currentUserMessage = persistUserMessage(conversationId, userId, question);
        List<ChatMessage> memoryMessages = recentMemoryMessages(conversationId, currentUserMessage.getId());
        String retrievalQuery = retrievalQuery(question, memoryMessages);

        return retrieveContext(userId, conversationId, retrievalQuery, startedAtNanos)
                .flatMapMany(context -> Flux.just(contextEvent(conversationId, memoryMessages.size(), context))
                        .concatWith(streamModelAnswer(
                                        conversationId,
                                        userId,
                                        role,
                                        authorization,
                                        currentUserMessage,
                                        null,
                                        systemPromptForRagContext(context),
                                        memoryMessages,
                                        question,
                                        startedAtNanos)));
    }

    private Flux<@NonNull ServerSentEvent<String>> streamCourseChat(UUID conversationId,
                                                                    UUID userId,
                                                                    Integer role,
                                                                    String authorization,
                                                                    ChatRequest request,
                                                                    long startedAtNanos) {
        ChatMessage currentUserMessage = persistUserMessage(conversationId, userId, request.message());
        List<ChatMessage> memoryMessages = recentMemoryMessages(conversationId, currentUserMessage.getId());
        AiCourseContext context = platformDataTool.loadCourseContext(request.courseId());
        RagContext chatMemoryContext = retrieveChatMemoryNow(userId, retrievalQuery(request.message(), memoryMessages));
        String courseSummary = platformDataTool.summarize(context);
        if (!chatMemoryContext.context().isBlank()) {
            courseSummary = courseSummary + "\n\n" + chatMemoryContext.context();
        }
        String systemPrompt = aiProperties.getChat().getCourseSystemPrompt()
                .replace("{context}", courseSummary);
        return Flux.just(contextEvent(conversationId, memoryMessages.size(), chatMemoryContext))
                .concatWith(streamModelAnswer(
                                conversationId,
                                userId,
                                role,
                                authorization,
                                currentUserMessage,
                                request.courseId(),
                                systemPrompt,
                                memoryMessages,
                                request.message(),
                                startedAtNanos));
    }

    private Flux<@NonNull ServerSentEvent<String>> streamModelAnswer(UUID conversationId,
                                                                     UUID userId,
                                                                     Integer role,
                                                                     String authorization,
                                                                     ChatMessage currentUserMessage,
                                                                     UUID courseId,
                                                                     String systemPrompt,
                                                                     List<ChatMessage> memoryMessages,
                                                                     String question,
                                                                     long startedAtNanos) {
        Sinks.Many<AgentSearchEvent> agentSearchSink = Sinks.many().unicast().onBackpressureBuffer();
        List<AgentSearchEvent> agentSearchLog = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AgentSearchEventEmitter emitter = event -> {
            if (cancelled.get()) {
                return;
            }
            agentSearchLog.add(event);
            agentSearchSink.tryEmitNext(event);
        };
        Flux<@NonNull ServerSentEvent<String>> agentSearchEvents = agentSearchSink.asFlux()
                .map(this::agentSearchEvent);
        Mono<@NonNull ServerSentEvent<String>> answerEvent = Mono.fromCallable(() -> callModelAnswer(
                        conversationId,
                        userId,
                        role,
                        authorization,
                        currentUserMessage,
                        courseId,
                        systemPrompt,
                        memoryMessages,
                         question,
                         emitter,
                         agentSearchLog,
                         cancelled))
                 .subscribeOn(Schedulers.boundedElastic())
                .doOnSubscribe(subscription -> log.info(
                        "AI chat model call started conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos)))
                .doOnNext(answer -> log.info(
                        "AI chat model call completed conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos)))
                .doOnError(error -> log.warn("AI chat model call errored conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos), error))
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL) {
                        cancelled.set(true);
                    }
                    agentSearchSink.tryEmitComplete();
                })
                .map(this::chunkEvent);
        return Flux.merge(agentSearchEvents, answerEvent);
    }

    private String callModelAnswer(UUID conversationId,
                                   UUID userId,
                                   Integer role,
                                   String authorization,
                                   ChatMessage currentUserMessage,
                                   UUID courseId,
                                   String systemPrompt,
                                    List<ChatMessage> memoryMessages,
                                    String question,
                                    AgentSearchEventEmitter emitter,
                                    List<AgentSearchEvent> agentSearchLog,
                                    AtomicBoolean cancelled) {
        Map<String, Object> toolContext = agentSearchContext(userId, role, authorization, conversationId, courseId, emitter);
        List<AgentSearchOutcome> preflightOutcomes = preflightAgentSearch(question, toolContext);
        String content = aiProviderCallGuard.call(() -> chatClient.prompt()
                .messages(modelMessages(systemPrompt, memoryMessages, question, role, preflightOutcomes))
                .tools(agentSearchTools)
                .toolContext(toolContext)
                .call()
                .content());
        if (emitter != null && !cancelled.get()) {
            emitter.emit(AgentSearchEvent.completed());
        }
        String answer = content == null ? "" : content;
        if (cancelled.get()) {
            log.info("AI chat model answer skipped after client cancellation conversationId={}", conversationId);
            return answer;
        }
        persistAssistantAnswer(conversationId, userId, currentUserMessage, courseId, answer, agentSearchLog);
        return answer;
    }

    private void persistAssistantAnswer(UUID conversationId,
                                        UUID userId,
                                        ChatMessage currentUserMessage,
                                        UUID courseId,
                                        String answer,
                                        List<AgentSearchEvent> agentSearchLog) {
        ChatMessage assistantMessage = persist(conversationId, MessageRole.ASSISTANT,
                answer, AiMessageType.TEXT, agentSearchPayload(agentSearchLog));
        indexChatTurn(userId, conversationId, currentUserMessage, assistantMessage, courseId);
    }

    private Map<String, Object> agentSearchPayload(List<AgentSearchEvent> events) {
        if (events == null || events.isEmpty()) {
            return null;
        }
        List<AgentSearchEvent> eventSnapshot;
        synchronized (events) {
            eventSnapshot = List.copyOf(events);
        }
        return Map.of(
                "agentSearch",
                Map.of(
                        "events", eventSnapshot,
                        "searches", agentSearches(eventSnapshot),
                        "items", agentSearchItems(eventSnapshot)
                ));
    }

    private List<Map<String, Object>> agentSearches(List<AgentSearchEvent> events) {
        Map<String, Map<String, Object>> searches = new LinkedHashMap<>();
        for (AgentSearchEvent event : events) {
            if (event == null || "completed".equals(event.phase())) {
                continue;
            }
            String key = StringUtils.hasText(event.searchId())
                    ? event.searchId()
                    : event.domain() + ":" + event.query();
            Map<String, Object> search = searches.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            search.putIfAbsent("searchId", event.searchId());
            search.putIfAbsent("domain", event.domain());
            search.putIfAbsent("query", event.query());
            search.put("label", event.label());
            search.put("phase", event.phase());
            search.put("total", event.total());
            search.put("occurredAt", event.occurredAt());
            putIfNotNull(search, "status", event.status());
            putIfNotNull(search, "reason", event.reason());
            putIfNotNull(search, "provider", event.provider());
            putIfNotNull(search, "durationMs", event.durationMs());
            putIfNotNull(search, "retryable", event.retryable());
            if (event.items() != null && !event.items().isEmpty()) {
                search.put("items", event.items());
            }
        }
        return List.copyOf(searches.values());
    }

    private List<Object> agentSearchItems(List<AgentSearchEvent> events) {
        return events.stream()
                .filter(Objects::nonNull)
                .flatMap(event -> event.items() == null ? java.util.stream.Stream.empty() : event.items().stream())
                .filter(Objects::nonNull)
                .map(item -> (Object) item)
                .toList();
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    String systemPromptForRagContext(RagContext context) {
        String prompt = context.useGeneralFallback()
                ? aiProperties.getChat().getGeneralSystemPrompt()
                : aiProperties.getRag().getSystemPrompt();
        return prompt.replace("{context}", context.context());
    }

    List<ChatMessage> recentMemoryMessages(UUID conversationId, UUID excludedMessageId) {
        int limit = Math.max(0, aiProperties.getChat().getMemoryWindowMessages());
        if (limit == 0) {
            return List.of();
        }

        List<ChatMessage> recentMessages = messageRepository.findRecentByConversationId(conversationId, limit + 1);
        List<ChatMessage> memoryMessages = recentMessages.stream()
                .filter(message -> !Objects.equals(message.getId(), excludedMessageId))
                .filter(this::isMemoryMessage)
                .limit(limit)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(memoryMessages);
        return memoryMessages;
    }

    List<Message> modelMessages(String systemPrompt, List<ChatMessage> memoryMessages, String question) {
        return modelMessages(systemPrompt, memoryMessages, question, null);
    }

    List<Message> modelMessages(String systemPrompt, List<ChatMessage> memoryMessages, String question, Integer role) {
        return modelMessages(systemPrompt, memoryMessages, question, role, List.of());
    }

    List<Message> modelMessages(String systemPrompt,
                                List<ChatMessage> memoryMessages,
                                String question,
                                Integer role,
                                List<AgentSearchOutcome> preflightOutcomes) {
        List<Message> messages = new ArrayList<>();
        String preflightContext = agentSearchPreflightContext(preflightOutcomes);
        String prompt = systemPrompt + "\n\n" + currentUserContext(role);
        if (StringUtils.hasText(preflightContext)) {
            prompt = prompt + "\n\n" + preflightContext;
        }
        messages.add(new SystemMessage(prompt + "\n\n" + agentSearchInstruction()));
        memoryMessages.stream()
                .map(this::toModelMessage)
                .filter(Objects::nonNull)
                .forEach(messages::add);
        messages.add(new UserMessage(question));
        return messages;
    }

    private String agentSearchInstruction() {
        return """
                AgentSearch tools are available for read-only searches within the current user's permissions.
                Use getCurrentUserProfile when the user asks who they are, their current account, name, or role.
                Use listMyCourses when the user asks which courses they teach, assist, learn, or can access.
                For questions about primary teaching courses, call listMyCourses with scope primaryTeaching.
                Use listCourseChapters when the user asks for a course's chapters, outline, syllabus structure, or catalog.
                Use searchCourseResources when the user asks to find course chapters, question banks, questions,
                course files, live practices, practice records, or other named resources within a specific course.
                Use search tools when the user asks about platform facts, course content, question banks, questions,
                live practices, personal knowledge documents, or prior chat memory. Do not invent platform facts; if search results are
                insufficient, say what is missing in user-facing language.
                Use searchWeb only when the user explicitly asks to search the web, go online, look up latest/recent/news/external
                public information, or the question depends on fresh public facts. Prefer platform and course search tools for
                platform facts. When using web results, cite readable webpage titles or domains naturally and do not present web
                results as internal platform facts.
                For today/now/latest/recent/news/this week/this month/this year questions, use getCurrentDateTime or
                the AgentSearch preflight SYSTEM_TIME result as the current date anchor before planning search keywords.
                Do not assume a stale year or month.
                If searchWeb returns EMPTY, you may try one refined query at most, using official names, English aliases, release dates,
                or publisher names. If searchWeb returns DISABLED, MISCONFIGURED, or FAILED, clearly say that web search is unavailable
                or failed; do not answer fresh/current facts from memory as if they were searched. Do not claim "latest" unless supported
                by returned WEB_SEARCH items.
                Use queryPlatformApi only for read-only platform facts that are not covered by the more specific tools.
                For user-facing answers, use readable names and Chinese role labels only. Do not reveal UUIDs,
                internal ids, courseId, sourceId, role numbers, or backend field names.
                """;
    }

    private String currentUserContext(Integer role) {
        String roleName = roleName(role);
        if (roleName.isBlank()) {
            return "当前登录用户角色：未知。";
        }
        return "当前登录用户角色：" + roleName + "。";
    }

    private List<AgentSearchOutcome> preflightAgentSearch(String question, Map<String, Object> toolContext) {
        if (!shouldPreflightCurrentDate(question)) {
            return List.of();
        }
        try {
            AgentSearchOutcome outcome = agentSearchTools.getCurrentDateTime(new ToolContext(toolContext));
            return outcome == null ? List.of() : List.of(outcome);
        } catch (RuntimeException e) {
            log.warn("AgentSearch current date preflight failed", e);
            return List.of();
        }
    }

    private boolean shouldPreflightCurrentDate(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        return TIME_SENSITIVE_TERMS.stream().anyMatch(normalized::contains);
    }

    private String agentSearchPreflightContext(List<AgentSearchOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return "";
        }
        List<AgentSearchOutcome> safeOutcomes = outcomes.stream()
                .filter(Objects::nonNull)
                .toList();
        if (safeOutcomes.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("AgentSearch preflight context:\n");
        for (AgentSearchOutcome outcome : safeOutcomes) {
            context.append("- domain=").append(outcome.domain())
                    .append(", status=").append(outcome.status())
                    .append(", provider=").append(outcome.provider())
                    .append(", query=").append(outcome.query())
                    .append("\n");
            if (StringUtils.hasText(outcome.reason())) {
                context.append("  reason=").append(outcome.reason()).append("\n");
            }
            for (AgentSearchItem item : outcome.items()) {
                context.append("  - sourceType=").append(item.sourceType())
                        .append(", sourceLabel=").append(item.sourceLabel())
                        .append(", title=").append(item.title());
                if (StringUtils.hasText(item.snippet())) {
                    context.append(", snippet=").append(item.snippet());
                }
                if (item.metadata() != null && !item.metadata().isEmpty()) {
                    context.append(", metadata=").append(item.metadata());
                }
                context.append("\n");
            }
        }
        return context.toString().stripTrailing();
    }

    private Map<String, Object> agentSearchContext(UUID userId,
                                                   Integer role,
                                                   String authorization,
                                                   UUID conversationId,
                                                   UUID courseId,
                                                   AgentSearchEventEmitter emitter) {
        Map<String, Object> context = new java.util.HashMap<>();
        context.put(AgentSearchTools.CONTEXT_USER_ID, userId.toString());
        if (role != null) {
            context.put(AgentSearchTools.CONTEXT_USER_ROLE, role.toString());
        }
        if (authorization != null && !authorization.isBlank()) {
            context.put(AgentSearchTools.CONTEXT_AUTHORIZATION, authorization);
        }
        context.put("conversationId", conversationId.toString());
        if (courseId != null) {
            context.put(AgentSearchTools.CONTEXT_COURSE_ID, courseId.toString());
        }
        if (emitter != null) {
            context.put(AgentSearchTools.CONTEXT_EVENT_EMITTER, emitter);
        }
        return context;
    }

    String retrievalQuery(String question, List<ChatMessage> memoryMessages) {
        int limit = Math.max(0, aiProperties.getRag().getHistoryQueryUserMessages());
        if (limit == 0 || memoryMessages.isEmpty()) {
            return question;
        }

        List<String> previousUserMessages = memoryMessages.stream()
                .filter(message -> MessageRole.USER.name().equals(message.getRole()))
                .map(ChatMessage::getContent)
                .filter(content -> content != null && !content.isBlank())
                .toList();
        previousUserMessages = previousUserMessages.stream()
                .skip(Math.max(0, previousUserMessages.size() - limit))
                .toList();
        if (previousUserMessages.isEmpty()) {
            return question;
        }
        return String.join("\n", previousUserMessages) + "\n" + question;
    }

    private boolean isMemoryMessage(ChatMessage message) {
        if (message.getContent() == null || message.getContent().isBlank()) {
            return false;
        }
        if (MessageRole.USER.name().equals(message.getRole())) {
            return AiMessageType.TEXT.name().equals(message.getMessageType());
        }
        if (!MessageRole.ASSISTANT.name().equals(message.getRole())) {
            return false;
        }
        return AiMessageType.TEXT.name().equals(message.getMessageType())
                || isCompletedGenerationMessage(message);
    }

    private Message toModelMessage(ChatMessage message) {
        if (MessageRole.USER.name().equals(message.getRole())) {
            return new UserMessage(message.getContent());
        }
        if (MessageRole.ASSISTANT.name().equals(message.getRole())) {
            return new AssistantMessage(memoryContent(message));
        }
        return null;
    }

    private boolean isCompletedGenerationMessage(ChatMessage message) {
        if (!AiMessageType.PAPER.name().equals(message.getMessageType())
                && !AiMessageType.QUESTION_SET.name().equals(message.getMessageType())) {
            return false;
        }
        Object status = message.getPayload() == null ? null : message.getPayload().get("generationStatus");
        if (status == null) {
            return true;
        }
        String normalizedStatus = status.toString();
        return !("processing".equalsIgnoreCase(normalizedStatus)
                || "failed".equalsIgnoreCase(normalizedStatus)
                || "error".equalsIgnoreCase(normalizedStatus));
    }

    private String memoryContent(ChatMessage message) {
        if (AiMessageType.PAPER.name().equals(message.getMessageType())) {
            return PAPER_MEMORY_LABEL + ":\n" + message.getContent();
        }
        if (AiMessageType.QUESTION_SET.name().equals(message.getMessageType())) {
            return QUESTION_SET_MEMORY_LABEL + ":\n" + message.getContent();
        }
        return message.getContent();
    }

    private void indexChatTurn(UUID userId,
                               UUID conversationId,
                               ChatMessage currentUserMessage,
                               ChatMessage assistantMessage,
                               UUID courseId) {
        Mono.fromRunnable(() -> chatVectorMemoryService.indexTurn(
                        userId, conversationId, currentUserMessage, assistantMessage, courseId))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<RagContext> retrieveContext(UUID userId,
                                             UUID conversationId,
                                             String query,
                                             long startedAtNanos) {
        return Mono.fromCallable(() -> retrieveContextNow(userId, query))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(RAG_RETRIEVAL_TIMEOUT)
                .doOnSubscribe(subscription -> log.info(
                        "AI chat RAG retrieval started conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos)))
                .doOnNext(context -> log.info(
                        "AI chat RAG retrieval completed conversationId={} strategy={} matchedChunkCount={} elapsedMs={}",
                        conversationId, context.strategy(), context.matchedChunkCount(), elapsedMs(startedAtNanos)))
                .onErrorResume(error -> {
                    if (error instanceof TimeoutException) {
                        log.warn("AI chat RAG retrieval timed out conversationId={} budgetMs={} elapsedMs={}",
                                conversationId, RAG_RETRIEVAL_TIMEOUT.toMillis(), elapsedMs(startedAtNanos));
                    } else {
                        log.warn("AI knowledge retrieval failed; continuing without retrieved context "
                                        + "conversationId={} elapsedMs={}",
                                conversationId, elapsedMs(startedAtNanos), error);
                    }
                    return Mono.just(RagContext.retrievalUnavailable(aiProperties.getChat().isGeneralFallbackEnabled()));
                });
    }

    RagContext retrieveContextNow(UUID userId, String question) {
        List<Document> knowledgeDocs = searchDocuments(
                question,
                aiProperties.getRag().getTopK(),
                aiProperties.getRag().getSimilarityThreshold(),
                sourceFilter(userId, KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC));
        List<Document> chatMemoryDocs = searchChatMemoryDocuments(userId, question);

        if (knowledgeDocs.isEmpty() && chatMemoryDocs.isEmpty()) {
            return RagContext.noMatch(aiProperties.getChat().isGeneralFallbackEnabled());
        }
        String context = contextFrom(knowledgeDocs, chatMemoryDocs);
        return RagContext.match(
                context,
                knowledgeDocs.size(),
                chatMemoryDocs.size(),
                docIds(knowledgeDocs),
                conversationIds(chatMemoryDocs),
                sourceTypes(knowledgeDocs, chatMemoryDocs));
    }

    private Set<String> docIds(List<Document> docs) {
        return docs.stream()
                .map(document -> document.getMetadata().get(KnowledgeBaseService.META_DOC_ID))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    RagContext retrieveChatMemoryNow(UUID userId, String question) {
        List<Document> chatMemoryDocs;
        try {
            chatMemoryDocs = searchChatMemoryDocuments(userId, question);
        } catch (RuntimeException e) {
            log.warn("AI chat memory retrieval failed; continuing without chat memory userId={}", userId, e);
            return RagContext.courseContext();
        }
        if (chatMemoryDocs.isEmpty()) {
            return RagContext.courseContext();
        }
        return RagContext.courseContext(
                contextBlock("Chat memory", chatMemoryDocs),
                chatMemoryDocs.size(),
                conversationIds(chatMemoryDocs),
                sourceTypes(List.of(), chatMemoryDocs));
    }

    private List<Document> searchChatMemoryDocuments(UUID userId, String question) {
        if (!aiProperties.getChatVectorMemory().isEnabled()) {
            return List.of();
        }
        return chatVectorMemoryService.activeMemoryDocuments(userId, searchDocuments(
                question,
                aiProperties.getChatVectorMemory().getTopK(),
                aiProperties.getChatVectorMemory().getSimilarityThreshold(),
                chatVectorMemoryService.activeMemoryFilter(userId)));
    }

    private List<Document> searchDocuments(String query, int topK, double similarityThreshold, String filterExpression) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(filterExpression)
                .build();
        List<Document> docs = aiProviderCallGuard.call(() -> vectorStoreProvider.getObject().similaritySearch(request));
        return docs == null ? List.of() : docs;
    }

    private String sourceFilter(UUID userId, String sourceType) {
        return "%s == '%s' && %s == '%s'"
                .formatted(KnowledgeBaseService.META_USER_ID, userId, KnowledgeBaseService.META_SOURCE_TYPE, sourceType);
    }

    private String contextFrom(List<Document> knowledgeDocs, List<Document> chatMemoryDocs) {
        return List.of(
                        contextBlock("Knowledge documents", knowledgeDocs),
                        contextBlock("Chat memory", chatMemoryDocs))
                .stream()
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String contextBlock(String title, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        return title + ":\n" + docs.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private Set<String> conversationIds(List<Document> docs) {
        return docs.stream()
                .map(document -> document.getMetadata().get(ChatVectorMemoryService.META_CONVERSATION_ID))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> sourceTypes(List<Document> knowledgeDocs, List<Document> chatMemoryDocs) {
        Set<String> values = new LinkedHashSet<>();
        if (!knowledgeDocs.isEmpty()) {
            values.add(KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC);
        }
        if (!chatMemoryDocs.isEmpty()) {
            values.add(ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN);
        }
        return values;
    }

    private ChatMessage persist(UUID conversationId,
                                MessageRole role,
                                String content,
                                AiMessageType messageType,
                                Map<String, Object> payload) {
        ChatMessage message = new ChatMessage();
        message.setId(UuidV7Generator.generate());
        message.setConversationId(conversationId);
        message.setRole(role.name());
        message.setContent(content);
        message.setMessageType(messageType.name());
        message.setPayload(payload);
        messageRepository.save(message);
        return message;
    }

    private ChatMessage persistUserMessage(UUID conversationId, UUID userId, String content) {
        return persistUserMessage(conversationId, userId, content, null);
    }

    private ChatMessage persistUserMessage(UUID conversationId,
                                           UUID userId,
                                           String content,
                                           Map<String, Object> payload) {
        ChatMessage message = persist(conversationId, MessageRole.USER, content, AiMessageType.TEXT, payload);
        conversationService.touchUpdatedAt(conversationId, userId);
        return message;
    }

    private Map<String, Object> generationRequestPayload(AiAgentMode mode, GenerationRequest request) {
        if (request == null) {
            return Map.of();
        }
        Map<String, Object> generationRequest = new LinkedHashMap<>();
        generationRequest.put("questionBankId", request.questionBankId() == null ? null : request.questionBankId().toString());
        generationRequest.put("questionCount", request.questionCount());
        generationRequest.put("questionType", request.questionType());
        generationRequest.put("difficulty", request.difficulty());
        generationRequest.put("scorePerQuestion", request.scorePerQuestion());
        generationRequest.put("totalScore", request.totalScore());
        generationRequest.put("totalEstimatedTime", request.totalEstimatedTime());
        generationRequest.put("paperName", request.paperName());
        generationRequest.put("paperType", request.paperType());
        generationRequest.put("requirement", request.requirement());
        generationRequest.put("chapterIds", request.chapterIds());
        generationRequest.put("knowledgePoints", request.knowledgePoints());
        generationRequest.put("abilityGoals", request.abilityGoals());
        return Map.of(
                "generationMode", mode.name(),
                "generationRequest", generationRequest);
    }

    private Mono<@NonNull ServerSentEvent<String>> asyncTitleUpdate(UUID conversationId,
                                                                    UUID userId,
                                                                    String message,
                                                                    String titleContext,
                                                                    String initialTitle,
                                                                    long startedAtNanos) {
        return Mono.fromCallable(() -> generateTitle(message, titleContext))
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(TITLE_GENERATION_TIMEOUT)
                .doOnSubscribe(subscription -> log.info(
                        "AI chat title generation started conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos)))
                .doOnNext(title -> log.info(
                        "AI chat title generation completed conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos)))
                .filter(title -> !title.equals(initialTitle))
                .doOnNext(title -> conversationService.updateConversationTitle(conversationId, title, userId))
                .map(title -> conversationEvent(conversationId, title))
                .onErrorResume(error -> {
                    if (error instanceof TimeoutException) {
                        log.warn("AI chat title generation timed out conversationId={} budgetMs={} elapsedMs={}",
                                conversationId, TITLE_GENERATION_TIMEOUT.toMillis(), elapsedMs(startedAtNanos));
                    } else {
                        log.warn("AI chat title generation failed conversationId={} elapsedMs={}",
                                conversationId, elapsedMs(startedAtNanos), error);
                    }
                    return Mono.empty();
                })
                .cache();
    }

    private String titleContext(ChatRequest request, AiAgentMode mode) {
        StringBuilder context = new StringBuilder("用户消息：\n").append(request.message());
        GenerationRequest generation = request.generation();
        if (mode == AiAgentMode.CHAT || generation == null) {
            return context.toString();
        }

        context.append("\n\n生成任务：").append(mode == AiAgentMode.PAPER ? "出卷" : "出题");
        appendTitleContext(context, "题目数量", generation.questionCount());
        appendTitleContext(context, "题型", generation.questionType());
        appendTitleContext(context, "难度", generation.difficulty());
        appendTitleContext(context, "试卷名称", generation.paperName());
        appendTitleContext(context, "试卷类型", generation.paperType());
        appendTitleContext(context, "要求", generation.requirement());
        appendTitleContext(context, "知识点", generation.knowledgePoints());
        appendTitleContext(context, "能力目标", generation.abilityGoals());
        return context.toString();
    }

    private void appendTitleContext(StringBuilder context, String label, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        context.append("\n").append(label).append("：").append(value);
    }

    private String generateTitle(String message, String titleContext) {
        if (!aiRuntimeGuard.isConfigured()) {
            return fallbackTitle(message);
        }
        String prompt = """
                请为这段 AI 会话生成一个简短标题。
                规则：
                - 使用与用户提问相同的语言。
                - 只返回标题本身。
                - 不要使用引号、markdown、句末标点或解释。
                - 中文不超过 16 个字，英文不超过 8 个单词。

                会话信息：
                %s
                """.formatted(titleContext);
        try {
            return normalizeTitle(aiProviderCallGuard.call(() -> chatClient.prompt().user(prompt).call().content()), message);
        } catch (RuntimeException exception) {
            return fallbackTitle(message);
        }
    }

    private String normalizeTitle(String title, String message) {
        if (title == null || title.isBlank()) {
            return fallbackTitle(message);
        }
        String normalized = compactTitle(title);
        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = compactTitle(normalized.substring(1, normalized.length() - 1));
        }
        if (normalized.isBlank()) {
            return fallbackTitle(message);
        }
        return limitTitle(normalized);
    }

    private String fallbackTitle(String message) {
        String title = compactTitle(message);
        return title.isBlank() ? "New conversation" : limitTitle(title);
    }

    private String compactTitle(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private String limitTitle(String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, MAX_TITLE_LENGTH);
    }

    private ServerSentEvent<String> conversationEvent(UUID conversationId, String title) {
        return ServerSentEvent.<String>builder(toJson(Map.of(
                        "id", conversationId,
                        "title", title
                )))
                .event("conversation")
                .build();
    }

    ServerSentEvent<String> contextEvent(UUID conversationId,
                                         int memoryMessageCount,
                                         RagContext context) {
        return ServerSentEvent.<String>builder(toJson(Map.of(
                        "conversationId", conversationId,
                        "memoryMessageCount", memoryMessageCount,
                        "ragStrategy", context.strategy().name(),
                        "matchedChunkCount", context.matchedChunkCount(),
                        "knowledgeMatchedCount", context.knowledgeMatchedCount(),
                        "chatMemoryMatchedCount", context.chatMemoryMatchedCount(),
                        "matchedSourceTypes", context.matchedSourceTypes(),
                        "matchedDocIds", context.matchedDocIds(),
                        "matchedConversationIds", context.matchedConversationIds(),
                        "agentSearchEnabled", true
                )))
                .event("context")
                .build();
    }

    private ServerSentEvent<String> chunkEvent(String chunk) {
        return ServerSentEvent.<String>builder(chunk)
                .event("chunk")
                .build();
    }

    private ServerSentEvent<String> generationResultEvent(UUID messageId,
                                                          String requestId,
                                                          AiAgentMode mode,
                                                          AiAgentResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", messageId);
        payload.put("requestId", requestId);
        payload.put("mode", mode.name());
        payload.put("content", result.content());
        payload.put("messageType", result.messageType().name());
        payload.put("payload", result.payload() == null ? Map.of() : result.payload());
        return ServerSentEvent.<String>builder(toJson(payload))
                .event("generation_result")
                .build();
    }

    private ServerSentEvent<String> keepaliveEvent() {
        return ServerSentEvent.<String>builder()
                .comment("keepalive")
                .build();
    }

    private ServerSentEvent<String> agentSearchEvent(AgentSearchEvent event) {
        return ServerSentEvent.<String>builder(toJson(event))
                .event("agent_search")
                .build();
    }

    private ServerSentEvent<String> generationStageEvent(UUID messageId, GenerationStageEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", messageId);
        payload.put("requestId", event.requestId());
        payload.put("mode", event.mode());
        payload.put("stage", event.stage());
        payload.put("status", event.status());
        payload.put("title", event.title());
        payload.put("summary", event.summary());
        payload.put("payload", event.payload());
        payload.put("timestamp", event.timestamp());
        return ServerSentEvent.<String>builder(toJson(payload))
                .event("generation_stage")
                .build();
    }

    private ServerSentEvent<String> generationProgressEvent(QuestionGenerationProgressEvent event, UUID messageId) {
        Object payload = event.payload() == null ? Map.of() : event.payload().get("event");
        String eventName = StringUtils.hasText(event.eventType()) ? event.eventType() : "generation_stage";
        if ("generation_stage".equals(eventName)) {
            return generationStageProgressEvent(event, messageId, payload);
        }
        return ServerSentEvent.<String>builder(toJson(payload == null ? event.payload() : payload))
                .event(eventName)
                .build();
    }

    private ServerSentEvent<String> generationStageProgressEvent(QuestionGenerationProgressEvent event,
                                                                 UUID messageId,
                                                                 Object payload) {
        if (payload instanceof GenerationStageEvent stageEvent) {
            return generationStageEvent(messageId, stageEvent);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (payload instanceof Map<?, ?> map) {
            map.forEach((key, value) -> data.put(String.valueOf(key), value));
        } else if (event.payload() != null) {
            data.putAll(event.payload());
        }
        data.put("messageId", messageId);
        data.putIfAbsent("requestId", event.requestId());
        return ServerSentEvent.<String>builder(toJson(data))
                .event("generation_stage")
                .build();
    }

    private Flux<@NonNull ServerSentEvent<String>> liveGenerationProgress(QuestionGenerationKafkaBridge kafkaBridge,
                                                                          String requestId,
                                                                          UUID messageId) {
        return kafkaBridge.progress(requestId)
                .map(event -> generationProgressEvent(event, messageId))
                .takeUntil(this::isTerminalGenerationStageEvent);
    }

    private ChatMessage generationMessage(UUID conversationId, UUID messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.AI_CONVERSATION_NOT_FOUND));
        if (!conversationId.equals(message.getConversationId()) || !isGenerationMessage(message)) {
            throw new BusinessException(ErrorCodes.AI_CONVERSATION_NOT_FOUND);
        }
        return message;
    }

    private boolean isGenerationMessage(ChatMessage message) {
        return AiMessageType.QUESTION_SET.name().equals(message.getMessageType())
                || AiMessageType.PAPER.name().equals(message.getMessageType());
    }

    private boolean isProcessingGeneration(Map<String, Object> payload) {
        return "processing".equals(textValue(payload.get("generationStatus")));
    }

    private AiAgentMode generationMode(ChatMessage message, Map<String, Object> payload) {
        String mode = textValue(payload.get("generationMode"));
        if (StringUtils.hasText(mode)) {
            try {
                return AiAgentMode.valueOf(mode);
            } catch (IllegalArgumentException ignored) {
                // Fall back to message type below.
            }
        }
        return AiMessageType.PAPER.name().equals(message.getMessageType()) ? AiAgentMode.PAPER : AiAgentMode.QUESTION;
    }

    private ServerSentEvent<String> generationSnapshotEvent(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getMessageType(),
                message.getPayload(),
                message.getCreatedAt());
        return ServerSentEvent.<String>builder(toJson(vo))
                .event("generation_snapshot")
                .build();
    }

    private boolean isTerminalGenerationStageEvent(ServerSentEvent<String> event) {
        if (!"generation_stage".equals(event.event()) || event.data() == null) {
            return false;
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(event.data(), new TypeReference<>() {
            });
            String status = textValue(payload.get("status"));
            String stage = textValue(payload.get("stage"));
            return "completed".equals(status) || "failed".equals(status) || "error".equals(status)
                    || "terminated".equals(status)
                    || "RESPONDED".equals(stage) || "FAILED".equals(stage) || "TERMINATED".equals(stage);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String textValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private ServerSentEvent<String> errorEvent(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return ServerSentEvent.<String>builder(businessException.getMessage())
                    .event("error")
                    .build();
        }
        log.error("AI chat stream failed", error);
        if (isProviderNotFound(error)) {
            return ServerSentEvent.<String>builder(AI_PROVIDER_NOT_FOUND_MESSAGE)
                    .event("error")
                    .build();
        }
        return ServerSentEvent.<String>builder("AI 回复暂时失败，请稍后再试。")
                .event("error")
                .build();
    }

    private boolean isProviderNotFound(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof NotFoundException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("404: Unknown")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private long elapsedMs(long startedAtNanos) {
        return Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private String roleName(Integer role) {
        UserRole userRole = UserRole.fromCode(role);
        if (userRole == UserRole.ADMIN) {
            return "管理员";
        }
        if (userRole == UserRole.TEACHER) {
            return "教师";
        }
        if (userRole == UserRole.STUDENT) {
            return "学生";
        }
        return "";
    }

    record RagContext(ChatStrategy strategy,
                      String context,
                      int matchedChunkCount,
                      int knowledgeMatchedCount,
                      int chatMemoryMatchedCount,
                      Set<String> matchedDocIds,
                      Set<String> matchedConversationIds,
                      Set<String> matchedSourceTypes) {
        static RagContext match(String context,
                                int knowledgeMatchedCount,
                                int chatMemoryMatchedCount,
                                Set<String> matchedDocIds,
                                Set<String> matchedConversationIds,
                                Set<String> matchedSourceTypes) {
            return new RagContext(
                    ChatStrategy.RAG_MATCH,
                    context,
                    knowledgeMatchedCount + chatMemoryMatchedCount,
                    knowledgeMatchedCount,
                    chatMemoryMatchedCount,
                    matchedDocIds,
                    matchedConversationIds,
                    matchedSourceTypes);
        }

        static RagContext noMatch(boolean generalFallbackEnabled) {
            if (generalFallbackEnabled) {
                return empty(ChatStrategy.GENERAL_FALLBACK, NO_RAG_CONTEXT);
            }
            return empty(ChatStrategy.RAG_ONLY_NO_MATCH, NO_RAG_CONTEXT);
        }

        static RagContext retrievalUnavailable(boolean generalFallbackEnabled) {
            if (generalFallbackEnabled) {
                return empty(ChatStrategy.GENERAL_FALLBACK, RAG_RETRIEVAL_UNAVAILABLE);
            }
            return empty(ChatStrategy.RAG_RETRIEVAL_UNAVAILABLE, RAG_RETRIEVAL_UNAVAILABLE);
        }

        static RagContext courseContext() {
            return empty(ChatStrategy.COURSE_CONTEXT, "");
        }

        static RagContext courseContext(String context,
                                        int chatMemoryMatchedCount,
                                        Set<String> matchedConversationIds,
                                        Set<String> matchedSourceTypes) {
            return new RagContext(
                    ChatStrategy.COURSE_CONTEXT,
                    context,
                    chatMemoryMatchedCount,
                    0,
                    chatMemoryMatchedCount,
                    Set.of(),
                    matchedConversationIds,
                    matchedSourceTypes);
        }

        private static RagContext empty(ChatStrategy strategy, String context) {
            return new RagContext(strategy, context, 0, 0, 0, Set.of(), Set.of(), Set.of());
        }

        boolean useGeneralFallback() {
            return strategy == ChatStrategy.GENERAL_FALLBACK;
        }
    }

    enum ChatStrategy {
        RAG_MATCH,
        GENERAL_FALLBACK,
        RAG_ONLY_NO_MATCH,
        RAG_RETRIEVAL_UNAVAILABLE,
        COURSE_CONTEXT
    }
}
