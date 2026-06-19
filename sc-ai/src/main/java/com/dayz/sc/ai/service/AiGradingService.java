package com.dayz.sc.ai.service;

import com.dayz.sc.ai.event.AiGradingEventPublisher;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingCompletedEvent;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiGradingService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int FLAG_ON = 1;
    private static final int FLAG_OFF = 0;

    private final ChatClient chatClient;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final AiGradingEventPublisher aiGradingEventPublisher;
    private final ObjectMapper objectMapper;

    public void grade(LivePracticeAiGradingRequestedEvent event) {
        if (!aiRuntimeGuard.isConfigured()) {
            publishFailure(event, aiRuntimeGuard.missingKeyMessage());
            return;
        }
        try {
            Map<String, Object> result = parse(callModel(event));
            BigDecimal score = decimal(result.get("score"));
            boolean isCorrect = bool(result.get("isCorrect"), score, event.score());
            String feedback = text(result.get("feedback"));
            publishCompleted(event, score, isCorrect ? FLAG_ON : FLAG_OFF, feedback);
        } catch (Exception exception) {
            log.warn("AI grading failed for submission {}", event.submissionId(), exception);
            publishFailure(event, "AI grading failed: " + exception.getMessage());
        }
    }

    private String callModel(LivePracticeAiGradingRequestedEvent event) throws JsonProcessingException {
        String prompt = """
                你是教育批改助手，请批改学生的主观题答案。
                请只返回合法 JSON，不要使用 markdown 代码块：
                {"score": 0, "isCorrect": false, "feedback": "brief feedback"}
                feedback 字段会直接展示给学生和教师。
                feedback 请使用学生作答语言，并采用自然的课堂反馈表达。
                不要在 feedback 中提到后端字段、ID、JSON、API、RAG、vector store、embedding、payload、messageType、DashScope、Kafka、Redis 或 SSE。

                满分：%s
                题目标题：%s
                题目内容：%s
                参考答案 JSON：%s
                学生答案：%s
                """.formatted(
                valueOrZero(event.score()),
                Objects.toString(event.questionTitle(), ""),
                Objects.toString(event.questionContent(), ""),
                objectMapper.writeValueAsString(event.answers()),
                Objects.toString(event.textAnswer(), "")
        );
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private Map<String, Object> parse(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("empty AI response");
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value != null) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private boolean bool(Object value, BigDecimal score, BigDecimal maxScore) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        BigDecimal safeMax = valueOrZero(maxScore);
        return safeMax.compareTo(BigDecimal.ZERO) > 0 && score.compareTo(safeMax) >= 0;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void publishCompleted(LivePracticeAiGradingRequestedEvent event,
                                  BigDecimal score,
                                  Integer isCorrect,
                                  String feedback) {
        aiGradingEventPublisher.publishCompleted(new LivePracticeAiGradingCompletedEvent(
                UuidV7Generator.generate(),
                event.submissionId(),
                event.groupId(),
                event.questionSnapshotId(),
                event.courseId(),
                event.classSessionId(),
                event.studentId(),
                STATUS_COMPLETED,
                isCorrect,
                score,
                feedback,
                null,
                "LIVE_PRACTICE_AI_GRADING_COMPLETED",
                Instant.now(),
                "sc-ai"
        ));
    }

    private void publishFailure(LivePracticeAiGradingRequestedEvent event, String errorMessage) {
        aiGradingEventPublisher.publishCompleted(new LivePracticeAiGradingCompletedEvent(
                UuidV7Generator.generate(),
                event.submissionId(),
                event.groupId(),
                event.questionSnapshotId(),
                event.courseId(),
                event.classSessionId(),
                event.studentId(),
                STATUS_FAILED,
                null,
                BigDecimal.ZERO,
                null,
                errorMessage,
                "LIVE_PRACTICE_AI_GRADING_FAILED",
                Instant.now(),
                "sc-ai"
        ));
    }
}
