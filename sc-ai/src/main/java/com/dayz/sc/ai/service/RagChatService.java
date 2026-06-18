package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.MessageRole;
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG 问答：向量检索 → 拼装上下文 → LLM 流式生成 → 持久化消息
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Service
public class RagChatService {

    private final ChatClient chatClient;
    private final ObjectProvider<@NonNull VectorStore> vectorStoreProvider;
    private final MessageRepository messageRepository;
    private final AiProperties aiProperties;

    /**
     * vectorStore 以 {@link ObjectProvider} 延迟获取：向量库 schema 初始化需调用
     * embedding 接口探测维度，若启动时即创建会在缺少 API Key 时拖垮整个服务启动
     * 延迟到首次实际检索时再实例化，使不依赖 LLM 的会话管理功能可独立运行
     */
    public RagChatService(ChatClient chatClient,
                          ObjectProvider<@NonNull VectorStore> vectorStoreProvider,
                          MessageRepository messageRepository,
                          AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.messageRepository = messageRepository;
        this.aiProperties = aiProperties;
    }

    /**
     * 流式问答用户消息先落库，助手回答在流结束后整体落库
     *
     * @param conversationId 已校验归属的会话 ID
     * @param userId         当前用户 ID（用于知识库范围过滤）
     * @param question       用户问题
     * @return 答案文本分片流
     */
    public Flux<@NonNull String> streamChat(UUID conversationId, UUID userId, String question) {
        // 1. 落库用户消息
        persist(conversationId, MessageRole.USER, question);

        // 2. 按用户隔离检索知识库
        String context = retrieveContext(userId, question);
        String systemPrompt = aiProperties.getRag().getSystemPrompt().replace("{context}", context);

        // 3. 流式调用 LLM，累积完整回答后落库
        StringBuilder answer = new StringBuilder();
        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .stream()
                .content()
                .doOnNext(answer::append)
                .doOnComplete(() -> persist(conversationId, MessageRole.ASSISTANT, answer.toString()));
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
            return "（暂无相关知识库内容）";
        }
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private void persist(UUID conversationId, MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(UuidV7Generator.generate());
        message.setConversationId(conversationId);
        message.setRole(role.name());
        message.setContent(content);
        messageRepository.save(message);
    }
}
