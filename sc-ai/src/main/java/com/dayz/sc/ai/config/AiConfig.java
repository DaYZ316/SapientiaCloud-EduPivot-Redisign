package com.dayz.sc.ai.config;

import com.dayz.sc.ai.service.KnowledgeBaseService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import redis.clients.jedis.RedisClient;

/**
 * Spring AI 相关 Bean 配置。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Configuration
public class AiConfig {

    /**
     * 全局 ChatClient，由自动配置的 OpenAI ChatModel（指向通义兼容端点）构建。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * Jedis RedisClient（RedisVectorStore 依赖 Jedis 而非 Lettuce）。
     */
    @Bean
    public RedisClient redisClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        if (password == null || password.isBlank()) {
            return RedisClient.create(host, port);
        }
        return RedisClient.create(host, port, null, password);
    }

    /**
     * 显式声明 Redis 向量库，注册按用户/文档过滤所需的 tag 元数据字段。
     * <p>
     * 标记为 {@link Lazy}：schema 初始化需要调用 embedding 接口探测向量维度，
     * 若启动时即初始化，会在缺少 API Key 时导致服务无法启动。延迟到首次实际使用
     * （问答检索或知识库入库）时再构建，使不依赖 LLM 的会话管理功能可独立运行。
     */
    @Bean
    @Lazy
    public RedisVectorStore vectorStore(
            RedisClient redisClient,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.vectorstore.redis.index-name:edupivot-ai-idx}") String indexName,
            @Value("${spring.ai.vectorstore.redis.prefix:edupivot:ai:vec:}") String prefix) {
        return RedisVectorStore.builder(redisClient, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .metadataFields(
                        RedisVectorStore.MetadataField.tag(KnowledgeBaseService.META_USER_ID),
                        RedisVectorStore.MetadataField.tag(KnowledgeBaseService.META_DOC_ID))
                .initializeSchema(true)
                .build();
    }
}
