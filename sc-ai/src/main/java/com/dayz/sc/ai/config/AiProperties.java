package com.dayz.sc.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * AI 模块业务配置（前缀 edupivot.ai）
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@ConfigurationProperties(prefix = "edupivot.ai")
@Getter
public class AiProperties {

    private final Chat chat = new Chat();
    private final Rag rag = new Rag();
    private final KnowledgeBase knowledgeBase = new KnowledgeBase();
    private final ChatVectorMemory chatVectorMemory = new ChatVectorMemory();
    private final AgentSearch agentSearch = new AgentSearch();
    private final LiveSummary liveSummary = new LiveSummary();

    /**
     * Chat strategy and prompt configuration.
     */
    @Getter
    @Setter
    public static class Chat {
        private boolean generalFallbackEnabled = true;
        private int memoryWindowMessages = 12;
        private String generalSystemPrompt = """
                你是「智语·云枢」的 AI 教学助手，服务对象是教师、学生和管理员。
                请始终使用用户提问的语言回答；默认使用中文。
                优先参考下方个人知识库资料。资料不足时，可以基于通用教学经验给出建议，但要自然说明资料不足。
                
                表达要求：
                - 用面向用户的自然语言，像在帮助一位真实的教学平台用户。
                - 先给结论或可执行建议，再补充依据；尽量简洁。
                - 如果资料不足，说明缺少哪些信息，并给出可以继续尝试的做法。
                - 除非用户明确询问系统实现、接口、部署或排障细节，不要输出以下开发/运维术语：RAG、vector store、embedding、payload、messageType、courseId、questionBankId、DashScope、API key、SSE、Kafka、Redis。
                - 不要编造用户、课程、章节、题库、学生、作业或平台事实。
                """ + LatexPromptRules.TEXT + """
                
                表达示例：
                - 不要说：“未检索到 RAG 上下文。”
                - 应该说：“暂时没有找到与你的问题直接相关的课程资料，我先根据通用教学经验给你一个建议。”
                - 不要说：“请检查 DashScope API key。”
                - 应该说：“AI 服务暂时不可用，请稍后再试或联系管理员。”
                
                个人知识库资料：
                {context}
                """;
        private String courseSystemPrompt = """
                你是「智语·云枢」的 AI 教学助手，服务对象是教师、学生和管理员。
                请始终使用用户提问的语言回答；默认使用中文。
                优先参考下方已授权的课程资料回答课程事实问题。
                
                表达要求：
                - 用面向用户的自然语言，像在帮助一位真实的教学平台用户。
                - 先给结论或可执行建议，再补充依据；尽量简洁。
                - 如果课程资料不足，说明缺少哪类课程信息，再给出可行的教学建议。
                - 除非用户明确询问系统实现、接口、部署或排障细节，不要输出以下开发/运维术语：RAG、vector store、embedding、payload、messageType、courseId、questionBankId、DashScope、API key、SSE、Kafka、Redis。
                - 不要编造课程、章节、题库、学生、作业或平台事实。
                """ + LatexPromptRules.TEXT + """
                
                表达示例：
                - 不要说：“courseId 没有对应上下文。”
                - 应该说：“我暂时没有看到这门课的更多资料。”
                - 不要说：“请检查 DashScope API key。”
                - 应该说：“AI 服务暂时不可用，请稍后再试或联系管理员。”
                
                已授权的课程资料：
                {context}
                """;
    }

    /**
     * RAG 检索与提示词参数
     */
    @Getter
    @Setter
    public static class Rag {
        /**
         * 向量检索返回的文档片段数
         */
        private int topK = 4;
        /**
         * 相似度阈值（0-1），低于此值的片段不参与
         */
        private double similarityThreshold = 0.5;
        private int historyQueryUserMessages = 2;
        /**
         * 系统提示词模板，{context} 处注入检索到的知识
         */
        private String systemPrompt = """
                你是「智语·云枢」的 AI 教学助手，服务对象是教师、学生和管理员。
                请始终使用用户提问的语言回答；默认使用中文。
                请基于下方知识库资料回答用户问题。资料不足时，请如实说明缺少哪些信息，不要编造。
                
                表达要求：
                - 用面向用户的自然语言，像在帮助一位真实的教学平台用户。
                - 先给结论或可执行建议，再补充依据；尽量简洁。
                - 除非用户明确询问系统实现、接口、部署或排障细节，不要输出以下开发/运维术语：RAG、vector store、embedding、payload、messageType、courseId、questionBankId、DashScope、API key、SSE、Kafka、Redis。
                """ + LatexPromptRules.TEXT + """
                
                表达示例：
                - 不要说：“未检索到 RAG 上下文。”
                - 应该说：“暂时没有找到与你的问题直接相关的课程资料。”
                
                知识库资料：
                {context}
                """;

    }

    /**
     * 知识库文档切分参数
     */
    @Getter
    @Setter
    public static class KnowledgeBase {
        /**
         * 文本切分目标长度（token 数）
         */
        private int chunkSize = 800;

    }

    @Getter
    @Setter
    public static class ChatVectorMemory {
        private boolean enabled = true;
        private int maxContentChars = 2000;
        private int topK = 3;
        private double similarityThreshold = 0.65;
    }

    @Getter
    @Setter
    public static class AgentSearch {
        private String gatewayBaseUrl = "http://localhost:39080";
        private List<String> openApiSources = List.of("auth", "course", "storage", "notification", "ai");
        private Duration timeout = Duration.ofSeconds(3);
        private int maxResponseChars = 4000;
        private boolean webSearchEnabled = true;
        private String webSearchEndpoint = "https://api.tavily.com/search";
        private String webSearchApiKey = "";
        private int webSearchMaxResults = 5;
        private Duration webSearchTimeout = Duration.ofSeconds(5);
        private int webSearchMaxSnippetChars = 500;
    }

    @Getter
    @Setter
    public static class LiveSummary {
        private final Asr asr = new Asr();
        private Duration summaryInterval = Duration.ofSeconds(60);
        private int summaryMinNewChars = 600;
        private int recentTranscriptLimit = 40;
        private int maxIncrementChars = 4000;

        @Getter
        @Setter
        public static class Asr {
            private String apiKey = "";
            private String websocketUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
            private String model = "qwen3-asr-flash-realtime";
            private String format = "pcm";
            private int sampleRate = 16000;
        }
    }
}
