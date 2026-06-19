package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.enums.MessageRole;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.errors.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
    private final ObjectMapper objectMapper;

    public RagChatService(ChatClient chatClient,
                          ObjectProvider<@NonNull VectorStore> vectorStoreProvider,
                          MessageRepository messageRepository,
                          ConversationService conversationService,
                          AiProperties aiProperties,
                          AiAgentService aiAgentService,
                          AiRuntimeGuard aiRuntimeGuard,
                          PlatformDataTool platformDataTool,
                          ChatVectorMemoryService chatVectorMemoryService,
                          ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
        this.aiProperties = aiProperties;
        this.aiAgentService = aiAgentService;
        this.aiRuntimeGuard = aiRuntimeGuard;
        this.platformDataTool = platformDataTool;
        this.chatVectorMemoryService = chatVectorMemoryService;
        this.objectMapper = objectMapper;
    }

    public Flux<@NonNull ServerSentEvent<String>> stream(ChatRequest request, UUID userId) {
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
            Flux<@NonNull ServerSentEvent<String>> chunks = streamContent(
                    request, userId, streamConversationId, mode, startedAtNanos);
            if (!newConversation) {
                return chunks;
            }

            Mono<@NonNull ServerSentEvent<String>> titleUpdate = asyncTitleUpdate(
                    streamConversationId, userId, request.message(), initialTitle, startedAtNanos);
            titleUpdate.subscribe();
            return Flux.just(conversationEvent(streamConversationId, initialTitle))
                    .concatWith(chunks.publish(sharedChunks -> Flux.merge(
                            sharedChunks,
                            titleUpdate.flux().takeUntilOther(sharedChunks.then())
                    )));
        }).onErrorResume(error -> Flux.just(errorEvent(error)));
    }

    private Flux<@NonNull ServerSentEvent<String>> streamContent(ChatRequest request,
                                                                 UUID userId,
                                                                 UUID conversationId,
                                                                 AiAgentMode mode,
                                                                 long startedAtNanos) {
        if (mode == AiAgentMode.CHAT && request.courseId() == null) {
            return streamRagChat(conversationId, userId, request.message(), startedAtNanos);
        }
        if (mode == AiAgentMode.CHAT) {
            return streamCourseChat(conversationId, userId, request, startedAtNanos);
        }

        persist(conversationId, MessageRole.USER, request.message(), AiMessageType.TEXT, null);
        log.info("AI chat agent run started conversationId={} mode={} elapsedMs={}",
                conversationId, mode, elapsedMs(startedAtNanos));
        AiAgentResult result = aiAgentService.run(request);
        persist(conversationId, MessageRole.ASSISTANT, result.content(), result.messageType(), result.payload());
        log.info("AI chat agent run completed conversationId={} mode={} elapsedMs={}",
                conversationId, mode, elapsedMs(startedAtNanos));
        return Flux.just(chunkEvent(result.content()));
    }

    private Flux<@NonNull ServerSentEvent<String>> streamRagChat(UUID conversationId,
                                                                 UUID userId,
                                                                 String question,
                                                                 long startedAtNanos) {
        ChatMessage currentUserMessage = persist(conversationId, MessageRole.USER, question, AiMessageType.TEXT, null);
        List<ChatMessage> memoryMessages = recentMemoryMessages(conversationId, currentUserMessage.getId());
        String retrievalQuery = retrievalQuery(question, memoryMessages);

        return retrieveContext(userId, conversationId, retrievalQuery, startedAtNanos)
                .flatMapMany(context -> Flux.just(contextEvent(conversationId, memoryMessages.size(), context))
                        .concatWith(streamModelAnswer(
                                        conversationId,
                                        userId,
                                        currentUserMessage,
                                        null,
                                        systemPromptForRagContext(context),
                                        memoryMessages,
                                        question,
                                        startedAtNanos)
                                .map(this::chunkEvent)));
    }

    private Flux<@NonNull ServerSentEvent<String>> streamCourseChat(UUID conversationId,
                                                                    UUID userId,
                                                                    ChatRequest request,
                                                                    long startedAtNanos) {
        ChatMessage currentUserMessage = persist(conversationId, MessageRole.USER, request.message(), AiMessageType.TEXT, null);
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
                                currentUserMessage,
                                request.courseId(),
                                systemPrompt,
                                memoryMessages,
                                request.message(),
                                startedAtNanos)
                        .map(this::chunkEvent));
    }

    private Flux<@NonNull String> streamModelAnswer(UUID conversationId,
                                                    UUID userId,
                                                    ChatMessage currentUserMessage,
                                                    UUID courseId,
                                                    String systemPrompt,
                                                    List<ChatMessage> memoryMessages,
                                                    String question,
                                                    long startedAtNanos) {
        StringBuilder answer = new StringBuilder();
        AtomicBoolean firstChunkLogged = new AtomicBoolean(false);
        return chatClient.prompt()
                .messages(modelMessages(systemPrompt, memoryMessages, question))
                .stream()
                .content()
                .doOnSubscribe(subscription -> log.info(
                        "AI chat model stream subscribed conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos)))
                .doOnNext(chunk -> {
                    if (firstChunkLogged.compareAndSet(false, true)) {
                        log.info("AI chat first model chunk received conversationId={} elapsedMs={}",
                                conversationId, elapsedMs(startedAtNanos));
                    }
                    answer.append(chunk);
                })
                .doOnComplete(() -> {
                    ChatMessage assistantMessage = persist(conversationId, MessageRole.ASSISTANT,
                            answer.toString(), AiMessageType.TEXT, null);
                    indexChatTurn(userId, conversationId, currentUserMessage, assistantMessage, courseId);
                    log.info("AI chat stream complete conversationId={} elapsedMs={}",
                            conversationId, elapsedMs(startedAtNanos));
                })
                .doOnError(error -> log.warn("AI chat stream errored conversationId={} elapsedMs={}",
                        conversationId, elapsedMs(startedAtNanos), error));
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
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        memoryMessages.stream()
                .map(this::toModelMessage)
                .filter(Objects::nonNull)
                .forEach(messages::add);
        messages.add(new UserMessage(question));
        return messages;
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
        if (!AiMessageType.TEXT.name().equals(message.getMessageType())) {
            return false;
        }
        return MessageRole.USER.name().equals(message.getRole())
                || MessageRole.ASSISTANT.name().equals(message.getRole());
    }

    private Message toModelMessage(ChatMessage message) {
        if (MessageRole.USER.name().equals(message.getRole())) {
            return new UserMessage(message.getContent());
        }
        if (MessageRole.ASSISTANT.name().equals(message.getRole())) {
            return new AssistantMessage(message.getContent());
        }
        return null;
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
        return searchDocuments(
                question,
                aiProperties.getChatVectorMemory().getTopK(),
                aiProperties.getChatVectorMemory().getSimilarityThreshold(),
                sourceFilter(userId, ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN));
    }

    private List<Document> searchDocuments(String query, int topK, double similarityThreshold, String filterExpression) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(filterExpression)
                .build();
        List<Document> docs = vectorStoreProvider.getObject().similaritySearch(request);
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

    private Mono<@NonNull ServerSentEvent<String>> asyncTitleUpdate(UUID conversationId,
                                                                    UUID userId,
                                                                    String message,
                                                                    String initialTitle,
                                                                    long startedAtNanos) {
        return Mono.fromCallable(() -> generateTitle(message))
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

    private String generateTitle(String message) {
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

                用户消息：
                %s
                """.formatted(message);
        try {
            return normalizeTitle(chatClient.prompt().user(prompt).call().content(), message);
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
                        "matchedConversationIds", context.matchedConversationIds()
                )))
                .event("context")
                .build();
    }

    private ServerSentEvent<String> chunkEvent(String chunk) {
        return ServerSentEvent.<String>builder(chunk)
                .event("chunk")
                .build();
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
