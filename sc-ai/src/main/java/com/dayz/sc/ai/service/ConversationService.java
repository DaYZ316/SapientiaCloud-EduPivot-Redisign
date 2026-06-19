package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.CreateConversationRequest;
import com.dayz.sc.ai.model.dto.UpdateConversationRequest;
import com.dayz.sc.ai.model.entity.Conversation;
import com.dayz.sc.ai.model.vo.ChatMessageVO;
import com.dayz.sc.ai.model.vo.ConversationVO;
import com.dayz.sc.ai.repository.ConversationRepository;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.util.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 会话管理业务逻辑
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatVectorMemoryService chatVectorMemoryService;

    @Transactional(rollbackFor = Exception.class)
    public UUID createConversation(CreateConversationRequest request, UUID userId) {
        return createConversation(request.title(), userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public UUID createConversation(String title, UUID userId) {
        Conversation conversation = new Conversation();
        conversation.setId(UuidV7Generator.generate());
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setPinned(0);
        conversation.setFavorited(0);
        conversationRepository.save(conversation);
        return conversation.getId();
    }

    public List<ConversationVO> listConversations(UUID userId, int page, int size) {
        return conversationRepository.findByUserId(userId, page, size).stream()
                .map(this::toVO)
                .toList();
    }

    public List<ChatMessageVO> listMessages(UUID conversationId, UUID userId) {
        requireOwnedConversation(conversationId, userId);
        return messageRepository.findByConversationId(conversationId).stream()
                .map(m -> new ChatMessageVO(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getMessageType(),
                        m.getPayload(),
                        m.getCreatedAt()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConversation(UUID conversationId, UpdateConversationRequest request, UUID userId) {
        Conversation conversation = requireOwnedConversation(conversationId, userId);
        if (request.title() != null) {
            conversation.setTitle(request.title());
        }
        if (request.pinned() != null) {
            conversation.setPinned(request.pinned() ? 1 : 0);
        }
        if (request.favorited() != null) {
            conversation.setFavorited(request.favorited() ? 1 : 0);
        }
        conversationRepository.update(conversation);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConversationTitle(UUID conversationId, String title, UUID userId) {
        Conversation conversation = requireOwnedConversation(conversationId, userId);
        conversation.setTitle(title);
        conversationRepository.update(conversation);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(UUID conversationId, UUID userId) {
        requireOwnedConversation(conversationId, userId);
        chatVectorMemoryService.deleteConversationMemory(conversationId, userId);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteByIdAndUserId(conversationId, userId);
    }

    /**
     * 校验会话归属，返回会话实体；不存在或不属于该用户时抛业务异常
     */
    public Conversation requireOwnedConversation(UUID conversationId, UUID userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.AI_CONVERSATION_NOT_FOUND));
    }

    private ConversationVO toVO(Conversation c) {
        return new ConversationVO(
                c.getId(),
                c.getTitle(),
                c.getPinned() != null && c.getPinned() == 1,
                c.getFavorited() != null && c.getFavorited() == 1,
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
