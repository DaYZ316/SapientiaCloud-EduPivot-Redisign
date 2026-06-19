package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.entity.Conversation;
import com.dayz.sc.ai.repository.ConversationRepository;
import com.dayz.sc.ai.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    @Test
    void deleteConversationShouldDeleteChatVectorMemoryBeforeDeletingRows() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        ChatVectorMemoryService chatVectorMemoryService = mock(ChatVectorMemoryService.class);
        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                chatVectorMemoryService);
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(conversationRepository.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(new Conversation()));

        service.deleteConversation(conversationId, userId);

        verify(chatVectorMemoryService).deleteConversationMemory(conversationId, userId);
        verify(messageRepository).deleteByConversationId(conversationId);
        verify(conversationRepository).deleteByIdAndUserId(conversationId, userId);
    }
}
