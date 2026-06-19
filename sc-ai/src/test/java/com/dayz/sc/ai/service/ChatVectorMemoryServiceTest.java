package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.ChatMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatVectorMemoryServiceTest {

    @Test
    void indexTurnShouldStoreQaContentWithIsolationMetadata() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatVectorMemoryService service = serviceWith(vectorStore, new AiProperties());
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        ChatMessage userMessage = message("What is photosynthesis?");
        ChatMessage assistantMessage = message("It converts light into chemical energy.");

        service.indexTurn(userId, conversationId, userMessage, assistantMessage, courseId);

        verify(vectorStore).add(org.mockito.ArgumentMatchers.<List<Document>>argThat(documents -> {
            Document document = documents.getFirst();
            return document.getText().equals("User: What is photosynthesis?\nAI: It converts light into chemical energy.")
                    && document.getMetadata().get(KnowledgeBaseService.META_SOURCE_TYPE)
                    .equals(ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)
                    && document.getMetadata().get(KnowledgeBaseService.META_USER_ID).equals(userId.toString())
                    && document.getMetadata().get(ChatVectorMemoryService.META_CONVERSATION_ID)
                    .equals(conversationId.toString())
                    && document.getMetadata().get(ChatVectorMemoryService.META_USER_MESSAGE_ID)
                    .equals(userMessage.getId().toString())
                    && document.getMetadata().get(ChatVectorMemoryService.META_ASSISTANT_MESSAGE_ID)
                    .equals(assistantMessage.getId().toString())
                    && document.getMetadata().get(ChatVectorMemoryService.META_COURSE_ID).equals(courseId.toString());
        }));
    }

    @Test
    void indexTurnShouldRespectEnabledFlag() {
        VectorStore vectorStore = mock(VectorStore.class);
        AiProperties properties = new AiProperties();
        properties.getChatVectorMemory().setEnabled(false);
        ChatVectorMemoryService service = serviceWith(vectorStore, properties);

        service.indexTurn(UUID.randomUUID(), UUID.randomUUID(), message("q"), message("a"), null);

        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void turnContentShouldTrimToConfiguredMaxLength() {
        AiProperties properties = new AiProperties();
        properties.getChatVectorMemory().setMaxContentChars(12);
        ChatVectorMemoryService service = serviceWith(mock(VectorStore.class), properties);

        String content = service.turnContent("abcdef", "ghijkl");

        assertThat(content).hasSize(12);
        assertThat(content).isEqualTo("User: abcdef");
    }

    @Test
    void deleteConversationMemoryShouldFilterBySourceConversationAndUser() {
        VectorStore vectorStore = mock(VectorStore.class);
        ChatVectorMemoryService service = serviceWith(vectorStore, new AiProperties());
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.deleteConversationMemory(conversationId, userId);

        verify(vectorStore).delete(org.mockito.ArgumentMatchers.<String>argThat(filter ->
                filter.contains(ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)
                        && filter.contains(conversationId.toString())
                        && filter.contains(userId.toString())));
    }

    @SuppressWarnings("unchecked")
    private ChatVectorMemoryService serviceWith(VectorStore vectorStore, AiProperties properties) {
        ObjectProvider<@org.jspecify.annotations.NonNull VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(vectorStore);
        return new ChatVectorMemoryService(provider, properties);
    }

    private ChatMessage message(String content) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setContent(content);
        return message;
    }
}
