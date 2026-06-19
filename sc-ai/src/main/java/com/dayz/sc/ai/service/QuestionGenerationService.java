package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_QUESTION_COUNT = 10;
    private static final int MAX_PAPER_COUNT = 50;

    private final ChatClient chatClient;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final PlatformDataTool platformDataTool;
    private final ObjectMapper objectMapper;

    public AiAgentResult generateQuestions(String userMessage, GenerationRequest request, AiCourseContext context) {
        if (!aiRuntimeGuard.isConfigured()) {
            return missingKeyResult(AiMessageType.QUESTION_SET);
        }
        GenerationRequest normalized = normalize(request, false);
        String response = callModel(userMessage, normalized, context, false);
        Map<String, Object> payload = parsePayload(response);
        return new AiAgentResult(formatQuestions(payload, false), AiMessageType.QUESTION_SET, payload);
    }

    public AiAgentResult generatePaper(String userMessage, GenerationRequest request, AiCourseContext context) {
        if (!aiRuntimeGuard.isConfigured()) {
            return missingKeyResult(AiMessageType.PAPER);
        }
        GenerationRequest normalized = normalize(request, true);
        String response = callModel(userMessage, normalized, context, true);
        Map<String, Object> payload = parsePayload(response);
        return new AiAgentResult(formatQuestions(payload, true), AiMessageType.PAPER, payload);
    }

    private GenerationRequest normalize(GenerationRequest request, boolean paper) {
        int fallbackCount = paper ? 20 : DEFAULT_QUESTION_COUNT;
        int maxCount = paper ? MAX_PAPER_COUNT : MAX_QUESTION_COUNT;
        int count = request != null && request.questionCount() != null ? request.questionCount() : fallbackCount;
        count = Math.max(1, Math.min(maxCount, count));
        return new GenerationRequest(
                request != null ? request.questionBankId() : null,
                count,
                request != null && request.questionType() != null ? request.questionType() : (paper ? 5 : 0),
                request != null && request.difficulty() != null ? request.difficulty() : 2,
                request != null ? request.scorePerQuestion() : null,
                request != null ? request.totalScore() : null,
                request != null ? request.totalEstimatedTime() : null,
                request != null ? request.paperName() : null,
                request != null ? request.paperType() : null,
                request != null ? request.requirement() : null,
                request != null ? request.chapterIds() : null,
                request != null ? request.knowledgePoints() : null,
                request != null ? request.abilityGoals() : null
        );
    }

    private String callModel(String userMessage, GenerationRequest request, AiCourseContext context, boolean paper) {
        String prompt = """
                你是教育测评助手。请只返回合法 JSON，不要使用 markdown 代码块。
                顶层 JSON 结构如下：
                {
                  "title": "string",
                  "type": "question_set or paper",
                  "questions": [
                    {
                      "questionTitle": "string",
                      "questionContent": "string",
                      "questionType": 0,
                      "difficulty": 2,
                      "score": 5,
                      "estimatedTime": 3,
                      "options": [{"label":"A","content":"string","correct":false,"explanation":"string"}],
                      "answers": [{"content":"string","explanation":"string","score":5}]
                    }
                  ]
                }
                题型编码：0 单选题，1 多选题，2 判断题，3 填空题，4 简答题，5 混合题。
                请使用用户的语言。答案和解析保持简洁。
                所有展示给用户的标题、题干、答案和解析都必须使用自然的课堂语言。
                不要在用户可见内容中提到后端字段、ID、JSON、API、RAG、vector store、embedding、payload、messageType、DashScope、Kafka、Redis 或 SSE。
                生成模式：%s
                用户需求：%s
                生成参数 JSON：
                %s
                平台课程资料：
                %s
                """.formatted(
                paper ? "paper" : "question_set",
                userMessage,
                toJson(request),
                platformDataTool.summarize(context));
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private Map<String, Object> parsePayload(String response) {
        if (response == null || response.isBlank()) {
            return Map.of("title", "AI generation result", "questions", java.util.List.of());
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", "AI generation result");
            fallback.put("raw", response);
            fallback.put("questions", java.util.List.of());
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private String formatQuestions(Map<String, Object> payload, boolean paper) {
        StringBuilder builder = new StringBuilder();
        builder.append(paper ? "## AI 出卷结果\n\n" : "## AI 出题结果\n\n");
        Object title = payload.get("title");
        if (title != null) {
            builder.append("**").append(title).append("**\n\n");
        }
        Object questionsValue = payload.get("questions");
        if (questionsValue instanceof java.util.List<?> questions && !questions.isEmpty()) {
            int index = 1;
            for (Object item : questions) {
                if (item instanceof Map<?, ?> question) {
                    builder.append(index++).append(". ")
                            .append(value(question, "questionTitle"))
                            .append("\n");
                    appendIfPresent(builder, question, "questionContent", "   ");
                    appendList(builder, (java.util.List<Object>) question.get("options"), "   - ");
                    appendList(builder, (java.util.List<Object>) question.get("answers"), "   答案: ");
                    builder.append('\n');
                }
            }
            return builder.toString();
        }
        if (payload.get("raw") != null) {
            return builder.append(payload.get("raw")).toString();
        }
        return builder.append("未生成可用题目。").toString();
    }

    private void appendIfPresent(StringBuilder builder, Map<?, ?> map, String key, String prefix) {
        Object value = map.get(key);
        if (value != null && !value.toString().isBlank()) {
            builder.append(prefix).append(value).append('\n');
        }
    }

    private void appendList(StringBuilder builder, java.util.List<Object> values, String prefix) {
        if (values == null) {
            return;
        }
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                Object label = map.get("label");
                Object content = map.get("content");
                if (content == null) {
                    content = map.get("answerContent");
                }
                builder.append(prefix);
                if (label != null) {
                    builder.append(label).append(". ");
                }
                builder.append(content != null ? content : map).append('\n');
            }
        }
    }

    private Object value(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private AiAgentResult missingKeyResult(AiMessageType type) {
        return new AiAgentResult(aiRuntimeGuard.missingKeyMessage(), type, Map.of(
                "status", "AI_NOT_CONFIGURED",
                "message", aiRuntimeGuard.missingKeyMessage()
        ));
    }
}
