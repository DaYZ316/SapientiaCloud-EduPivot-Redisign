package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Service
public class ChatVectorMemoryService {

    public static final String META_SOURCE_TYPE_CHAT_TURN = "CHAT_TURN";
    public static final String META_CONVERSATION_ID = "conversationId";
    public static final String META_USER_MESSAGE_ID = "userMessageId";
    public static final String META_ASSISTANT_MESSAGE_ID = "assistantMessageId";
    public static final String META_COURSE_ID = "courseId";
    public static final String META_DELETED = "deleted";

    private static final String USER_PREFIX = "User: ";
    private static final String AI_PREFIX = "\nAI: ";

    private final ObjectProvider<@NonNull VectorStore> vectorStoreProvider;
    private final AiProperties aiProperties;
    private final AiProviderCallGuard aiProviderCallGuard;
    private final Set<String> deletedConversationKeys = ConcurrentHashMap.newKeySet();

    public ChatVectorMemoryService(ObjectProvider<@NonNull VectorStore> vectorStoreProvider,
                                   AiProperties aiProperties,
                                   AiProviderCallGuard aiProviderCallGuard) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.aiProperties = aiProperties;
        this.aiProviderCallGuard = aiProviderCallGuard;
    }

    public void indexTurn(UUID userId,
                          UUID conversationId,
                          ChatMessage userMessage,
                          ChatMessage assistantMessage,
                          UUID courseId) {
        if (!aiProperties.getChatVectorMemory().isEnabled()) {
            return;
        }
        if (userId == null || conversationId == null || userMessage == null || assistantMessage == null) {
            return;
        }
        if (isConversationMarkedDeleted(conversationId, userId)) {
            log.info("AI chat vector memory indexing skipped for deleted conversation conversationId={}", conversationId);
            return;
        }
        String content = turnContent(userMessage.getContent(), assistantMessage.getContent());
        if (!StringUtils.hasText(content)) {
            return;
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(KnowledgeBaseService.META_SOURCE_TYPE, META_SOURCE_TYPE_CHAT_TURN);
        metadata.put(KnowledgeBaseService.META_USER_ID, userId.toString());
        metadata.put(META_CONVERSATION_ID, conversationId.toString());
        metadata.put(META_USER_MESSAGE_ID, userMessage.getId().toString());
        metadata.put(META_ASSISTANT_MESSAGE_ID, assistantMessage.getId().toString());
        metadata.put(META_DELETED, "false");
        if (courseId != null) {
            metadata.put(META_COURSE_ID, courseId.toString());
        }

        try {
            aiProviderCallGuard.run(() -> vectorStoreProvider.getObject().add(List.of(new Document(content, metadata))));
            log.info("AI chat vector memory indexed conversationId={} userMessageId={} assistantMessageId={}",
                    conversationId, userMessage.getId(), assistantMessage.getId());
        } catch (Exception e) {
            log.warn("AI chat vector memory indexing failed conversationId={} assistantMessageId={}",
                    conversationId, assistantMessage.getId(), e);
        }
    }

    public void deleteConversationMemory(UUID conversationId, UUID userId) {
        if (conversationId == null || userId == null) {
            return;
        }
        deletedConversationKeys.add(conversationKey(conversationId, userId));
        try {
            vectorStoreProvider.getObject().delete("%s == '%s' && %s == '%s' && %s == '%s'"
                    .formatted(KnowledgeBaseService.META_SOURCE_TYPE, META_SOURCE_TYPE_CHAT_TURN,
                            META_CONVERSATION_ID, conversationId, KnowledgeBaseService.META_USER_ID, userId));
            log.info("AI chat vector memory deleted conversationId={} userId={}", conversationId, userId);
        } catch (Exception e) {
            log.warn("AI chat vector memory deletion failed conversationId={} userId={}", conversationId, userId, e);
        }
    }

    public List<Document> activeMemoryDocuments(UUID userId, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream()
                .filter(document -> !isDeletedMemoryDocument(userId, document))
                .toList();
    }

    String activeMemoryFilter(UUID userId) {
        return "%s == '%s' && %s == '%s' && %s == '%s'"
                .formatted(KnowledgeBaseService.META_USER_ID, userId,
                        KnowledgeBaseService.META_SOURCE_TYPE, META_SOURCE_TYPE_CHAT_TURN,
                        META_DELETED, "false");
    }

    private boolean isDeletedMemoryDocument(UUID userId, Document document) {
        if (document == null || document.getMetadata() == null) {
            return false;
        }
        Object deleted = document.getMetadata().get(META_DELETED);
        if (deleted != null && Boolean.parseBoolean(deleted.toString())) {
            return true;
        }
        Object conversationId = document.getMetadata().get(META_CONVERSATION_ID);
        return conversationId != null && deletedConversationKeys.contains(conversationKey(conversationId.toString(), userId));
    }

    private boolean isConversationMarkedDeleted(UUID conversationId, UUID userId) {
        return deletedConversationKeys.contains(conversationKey(conversationId, userId));
    }

    private String conversationKey(UUID conversationId, UUID userId) {
        return userId + ":" + conversationId;
    }

    private String conversationKey(String conversationId, UUID userId) {
        return userId + ":" + conversationId;
    }

    String turnContent(String userContent, String assistantContent) {
        if (!StringUtils.hasText(userContent) || !StringUtils.hasText(assistantContent)) {
            return null;
        }
        String content = USER_PREFIX + userContent.strip() + AI_PREFIX + assistantContent.strip();
        int maxLength = Math.max(0, aiProperties.getChatVectorMemory().getMaxContentChars());
        if (maxLength > 0 && content.length() > maxLength) {
            return content.substring(0, maxLength);
        }
        return content;
    }
}
