package com.dayz.sc.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 模块业务配置（前缀 edupivot.ai）
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@ConfigurationProperties(prefix = "edupivot.ai")
@Getter
public class AiProperties {

    private final Rag rag = new Rag();
    private final KnowledgeBase knowledgeBase = new KnowledgeBase();

    /**
     * RAG 检索与提示词参数
     */
    @Getter
    @Setter
    public static class Rag {
        /** 向量检索返回的文档片段数 */
        private int topK = 4;
        /** 相似度阈值（0-1），低于此值的片段不参与 */
        private double similarityThreshold = 0.5;
        /** 系统提示词模板，{context} 处注入检索到的知识 */
        private String systemPrompt = """
                你是「智语·云枢」的 AI 教学助手请基于下面提供的课程知识库内容回答用户问题
                如果知识库内容不足以回答，请如实说明，不要编造

                知识库内容：
                {context}
                """;

    }

    /**
     * 知识库文档切分参数
     */
    @Getter
    @Setter
    public static class KnowledgeBase {
        /** 文本切分目标长度（token 数） */
        private int chunkSize = 800;

    }
}
