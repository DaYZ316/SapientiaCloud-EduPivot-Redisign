package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.enums.MessageRole;
import com.dayz.sc.ai.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceTest {

    @Test
    void retrieveContextNowShouldReturnGeneralFallbackWhenNothingMatches() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        RagChatService service = serviceWith(vectorStore, new AiProperties());

        RagChatService.RagContext context = service.retrieveContextNow(UUID.randomUUID(), "question");

        assertThat(context.strategy()).isEqualTo(RagChatService.ChatStrategy.GENERAL_FALLBACK);
        assertThat(context.matchedChunkCount()).isZero();
        assertThat(context.knowledgeMatchedCount()).isZero();
        assertThat(context.chatMemoryMatchedCount()).isZero();
    }

    @Test
    void retrieveContextNowShouldMergeKnowledgeAndChatMemoryMatches() {
        UUID conversationId = UUID.randomUUID();
        VectorStore vectorStore = vectorStoreWithSourceResults(
                List.of(new Document("knowledge A", Map.of(KnowledgeBaseService.META_DOC_ID, "doc-a"))),
                List.of(new Document("User: old\nAI: answer", Map.of(
                        ChatVectorMemoryService.META_CONVERSATION_ID, conversationId.toString()))));
        RagChatService service = serviceWith(vectorStore, new AiProperties());

        RagChatService.RagContext context = service.retrieveContextNow(UUID.randomUUID(), "question");

        assertThat(context.strategy()).isEqualTo(RagChatService.ChatStrategy.RAG_MATCH);
        assertThat(context.context()).contains("knowledge A", "User: old");
        assertThat(context.matchedChunkCount()).isEqualTo(2);
        assertThat(context.knowledgeMatchedCount()).isEqualTo(1);
        assertThat(context.chatMemoryMatchedCount()).isEqualTo(1);
        assertThat(context.matchedDocIds()).containsExactly("doc-a");
        assertThat(context.matchedConversationIds()).containsExactly(conversationId.toString());
        assertThat(context.matchedSourceTypes())
                .containsExactly(KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC,
                        ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN);
    }

    @Test
    void retrieveContextNowShouldFilterByUserIdAndSourceTypes() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        RagChatService service = serviceWith(vectorStore, new AiProperties());
        UUID userId = UUID.randomUUID();

        service.retrieveContextNow(userId, "question");

        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                filterContains(request, userId.toString())
                        && filterContains(request, KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC)));
        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                filterContains(request, userId.toString())
                        && filterContains(request, ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)));
    }

    @Test
    void retrieveChatMemoryNowShouldUseChatMemoryOnly() {
        UUID conversationId = UUID.randomUUID();
        VectorStore vectorStore = vectorStoreWithSourceResults(
                List.of(new Document("knowledge A", Map.of(KnowledgeBaseService.META_DOC_ID, "doc-a"))),
                List.of(new Document("User: old\nAI: answer", Map.of(
                        ChatVectorMemoryService.META_CONVERSATION_ID, conversationId.toString()))));
        RagChatService service = serviceWith(vectorStore, new AiProperties());

        RagChatService.RagContext context = service.retrieveChatMemoryNow(UUID.randomUUID(), "question");

        assertThat(context.strategy()).isEqualTo(RagChatService.ChatStrategy.COURSE_CONTEXT);
        assertThat(context.context()).contains("User: old").doesNotContain("knowledge A");
        assertThat(context.knowledgeMatchedCount()).isZero();
        assertThat(context.chatMemoryMatchedCount()).isEqualTo(1);
    }

    @Test
    void recentMemoryMessagesShouldKeepTextUserAndAssistantMessagesInAscendingOrder() {
        UUID conversationId = UUID.randomUUID();
        UUID currentMessageId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findRecentByConversationId(conversationId, 13)).thenReturn(List.of(
                message(currentMessageId, MessageRole.USER, AiMessageType.TEXT, "current"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.TEXT, "old answer 2"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "old question 2"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.PAPER, "paper payload"),
                message(UUID.randomUUID(), MessageRole.SYSTEM, AiMessageType.TEXT, "system"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "old question 1")
        ));
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties(), messageRepository);

        List<ChatMessage> messages = service.recentMemoryMessages(conversationId, currentMessageId);

        assertThat(messages)
                .extracting(ChatMessage::getContent)
                .containsExactly("old question 1", "old question 2", "old answer 2");
    }

    @Test
    void modelMessagesShouldBuildSystemHistoryAndCurrentQuestion() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());

        List<Message> messages = service.modelMessages("SYSTEM", List.of(
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "my name is Zhang San"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.TEXT, "ok")
        ), "what is my name?");

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("my name is Zhang San");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3).getText()).isEqualTo("what is my name?");
    }

    @Test
    void retrievalQueryShouldAppendRecentUserMessagesOnly() {
        AiProperties properties = new AiProperties();
        properties.getRag().setHistoryQueryUserMessages(2);
        RagChatService service = serviceWith(mock(VectorStore.class), properties);

        String query = service.retrievalQuery("current", List.of(
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "first"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.TEXT, "first answer"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "second"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "third")
        ));

        assertThat(query).isEqualTo("second\nthird\ncurrent");
    }

    @Test
    void contextEventShouldExposeMemoryAndRagMetadata() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());
        UUID conversationId = UUID.randomUUID();

        ServerSentEvent<String> event = service.contextEvent(
                conversationId,
                3,
                RagChatService.RagContext.match(
                        "ctx",
                        1,
                        1,
                        Set.of("doc-a"),
                        Set.of("conv-a"),
                        Set.of(KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC,
                                ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)));

        assertThat(event.event()).isEqualTo("context");
        assertThat(event.data())
                .contains(conversationId.toString())
                .contains("\"memoryMessageCount\":3")
                .contains("\"ragStrategy\":\"RAG_MATCH\"")
                .contains("\"matchedChunkCount\":2")
                .contains("\"knowledgeMatchedCount\":1")
                .contains("\"chatMemoryMatchedCount\":1")
                .contains("doc-a")
                .contains("conv-a")
                .contains(ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN);
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore, AiProperties properties) {
        return serviceWith(vectorStore, properties, mock(MessageRepository.class));
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository) {
        ObjectProvider<@org.jspecify.annotations.NonNull VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(vectorStore);
        return new RagChatService(
                mock(ChatClient.class),
                provider,
                messageRepository,
                mock(ConversationService.class),
                properties,
                mock(AiAgentService.class),
                mock(AiRuntimeGuard.class),
                mock(PlatformDataTool.class),
                mock(ChatVectorMemoryService.class),
                new ObjectMapper()
        );
    }

    private VectorStore vectorStoreWithSourceResults(List<Document> knowledgeDocs, List<Document> chatMemoryDocs) {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            String filter = request.getFilterExpression() == null
                    ? ""
                    : request.getFilterExpression().toString();
            if (filter.contains(ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)) {
                return chatMemoryDocs;
            }
            if (filter.contains(KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC)) {
                return knowledgeDocs;
            }
            return List.of();
        });
        return vectorStore;
    }

    private boolean filterContains(SearchRequest request, String value) {
        return request.getFilterExpression() != null
                && request.getFilterExpression().toString().contains(value);
    }

    private ChatMessage message(UUID id, MessageRole role, AiMessageType messageType, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setRole(role.name());
        message.setMessageType(messageType.name());
        message.setContent(content);
        return message;
    }
}
