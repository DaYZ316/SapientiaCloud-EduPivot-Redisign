package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.entity.Conversation;
import com.dayz.sc.ai.repository.ConversationRepository;
import com.dayz.sc.ai.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class ConversationServiceTest {

    @Test
    void deleteConversationShouldDeleteRowsBeforeChatVectorMemory() {
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

        InOrder inOrder = inOrder(messageRepository, conversationRepository, chatVectorMemoryService);
        inOrder.verify(messageRepository).deleteByConversationId(conversationId);
        inOrder.verify(conversationRepository).deleteByIdAndUserId(conversationId, userId);
        inOrder.verify(chatVectorMemoryService).deleteConversationMemory(conversationId, userId);
    }
}
