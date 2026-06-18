package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.enums.MessageRole;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.util.UuidV7Generator;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RagChatService {

    private final ChatClient chatClient;
    private final ObjectProvider<@NonNull VectorStore> vectorStoreProvider;
    private final MessageRepository messageRepository;
    private final AiProperties aiProperties;
    private final AiAgentService aiAgentService;
    private final AiRuntimeGuard aiRuntimeGuard;

    public RagChatService(ChatClient chatClient,
                          ObjectProvider<@NonNull VectorStore> vectorStoreProvider,
                          MessageRepository messageRepository,
                          AiProperties aiProperties,
                          AiAgentService aiAgentService,
                          AiRuntimeGuard aiRuntimeGuard) {
        this.chatClient = chatClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.messageRepository = messageRepository;
        this.aiProperties = aiProperties;
        this.aiAgentService = aiAgentService;
        this.aiRuntimeGuard = aiRuntimeGuard;
    }

    public Flux<@NonNull String> stream(ChatRequest request, UUID userId) {
        AiAgentMode mode = AiAgentMode.resolve(request.agentMode(), request.message());
        if (mode == AiAgentMode.CHAT && request.courseId() == null) {
            return streamRagChat(request.conversationId(), userId, request.message());
        }

        persist(request.conversationId(), MessageRole.USER, request.message(), AiMessageType.TEXT, null);
        AiAgentResult result = aiAgentService.run(request);
        persist(request.conversationId(), MessageRole.ASSISTANT, result.content(), result.messageType(), result.payload());
        return Flux.just(result.content());
    }

    private Flux<@NonNull String> streamRagChat(UUID conversationId, UUID userId, String question) {
        aiRuntimeGuard.requireConfigured();
        persist(conversationId, MessageRole.USER, question, AiMessageType.TEXT, null);

        String context = retrieveContext(userId, question);
        String systemPrompt = aiProperties.getRag().getSystemPrompt().replace("{context}", context);

        StringBuilder answer = new StringBuilder();
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .stream()
                .content()
                .doOnNext(answer::append)
                .doOnComplete(() -> persist(conversationId, MessageRole.ASSISTANT, answer.toString(), AiMessageType.TEXT, null));
    }

    private String retrieveContext(UUID userId, String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(aiProperties.getRag().getTopK())
                .similarityThreshold(aiProperties.getRag().getSimilarityThreshold())
                .filterExpression("%s == '%s'".formatted(KnowledgeBaseService.META_USER_ID, userId))
                .build();
        List<Document> docs = vectorStoreProvider.getObject().similaritySearch(request);
        if (docs.isEmpty()) {
            return "(No relevant knowledge base content found.)";
        }
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private void persist(UUID conversationId,
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
    }
}
