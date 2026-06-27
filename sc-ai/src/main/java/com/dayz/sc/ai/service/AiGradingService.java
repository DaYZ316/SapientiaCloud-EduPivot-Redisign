package com.dayz.sc.ai.service;

import com.dayz.sc.ai.event.AiGradingEventPublisher;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingCompletedEvent;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * AiGradingService.
 *
 * @author DaYZ
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGradingService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final int FLAG_ON = 1;
    private static final int FLAG_OFF = 0;
    private static final String MARKDOWN_CODE_FENCE = "```";
    private static final Duration DEFAULT_GRADING_TIMEOUT = Duration.ofSeconds(30);

    private final ChatClient chatClient;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final AiGradingEventPublisher aiGradingEventPublisher;
    private final ObjectMapper objectMapper;
    private final AiProviderCallGuard aiProviderCallGuard;
    private final ExecutorService gradingExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Value("${edupivot.ai.grading.timeout:30s}")
    private Duration gradingTimeout = DEFAULT_GRADING_TIMEOUT;

    public void grade(LivePracticeAiGradingRequestedEvent event) {
        if (!aiRuntimeGuard.isConfigured()) {
            publishFailure(event, aiRuntimeGuard.missingKeyMessage());
            return;
        }
        try {
            Map<String, Object> result = parse(callModelWithTimeout(event));
            BigDecimal score = clampScore(decimal(result.get("score")), event.score());
            boolean isCorrect = bool(result.get("isCorrect"), score, event.score());
            String feedback = text(result.get("feedback"));
            publishCompleted(event, score, isCorrect ? FLAG_ON : FLAG_OFF, feedback);
        } catch (Exception exception) {
            log.warn("AI grading failed for submission {}", event.submissionId(), exception);
            if (exception instanceof AiGradingResultPublishException publishException) {
                throw publishException;
            }
            publishFailure(event, "AI grading failed: " + exception.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        gradingExecutor.shutdownNow();
    }

    private String callModelWithTimeout(LivePracticeAiGradingRequestedEvent event) throws JsonProcessingException {
        Duration timeout = effectiveGradingTimeout();
        Future<String> future = gradingExecutor.submit(() -> callModel(event));
        try {
            return future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new IllegalStateException("AI grading timed out after " + timeout.toMillis() + " ms", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI grading interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof JsonProcessingException jsonProcessingException) {
                throw jsonProcessingException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("AI grading failed", cause);
        }
    }

    private Duration effectiveGradingTimeout() {
        if (gradingTimeout == null || gradingTimeout.isZero() || gradingTimeout.isNegative()) {
            return DEFAULT_GRADING_TIMEOUT;
        }
        return gradingTimeout;
    }

    private String callModel(LivePracticeAiGradingRequestedEvent event) throws JsonProcessingException {
        String prompt = """
                你是教育批改助手，请批改学生的简答题答案。
                必须只依据题干、满分、参考答案和教师判题要求评分；不要联网，不要引入题目外部资料。
                若学生答案与参考答案表达不同但含义正确，可按教师判题要求给分。
                请只返回合法 JSON，不要使用 markdown 代码块：
                {"score": 0, "isCorrect": false, "feedback": "brief feedback"}
                score 必须是 0 到满分之间的数字。
                isCorrect 仅在学生答案应得满分时为 true。
                feedback 会直接展示给学生和教师，请使用学生作答语言，简短说明得分原因和改进点。
                不要在 feedback 中提到后端字段、ID、JSON、API、RAG、vector store、embedding、payload、messageType、DashScope、Kafka、Redis 或 SSE。
                满分：%s
                题目标题：%s
                题目内容：%s
                参考答案 JSON：%s
                教师判题要求：%s
                学生答案：%s
                """.formatted(
                valueOrZero(event.score()),
                Objects.toString(event.questionTitle(), ""),
                Objects.toString(event.questionContent(), ""),
                objectMapper.writeValueAsString(event.answers()),
                Objects.toString(event.gradingRequirement(), ""),
                Objects.toString(event.textAnswer(), "")
        );
        return aiProviderCallGuard.call(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());
    }

    private Map<String, Object> parse(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("empty AI response");
        }
        String json = response.trim();
        if (json.startsWith(MARKDOWN_CODE_FENCE)) {
            json = json.replaceFirst("^" + MARKDOWN_CODE_FENCE + "(?:json)?", "")
                    .replaceFirst(MARKDOWN_CODE_FENCE + "$", "")
                    .trim();
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

    private BigDecimal clampScore(BigDecimal score, BigDecimal maxScore) {
        BigDecimal safeScore = valueOrZero(score);
        BigDecimal safeMax = valueOrZero(maxScore);
        if (safeScore.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (safeMax.compareTo(BigDecimal.ZERO) > 0 && safeScore.compareTo(safeMax) > 0) {
            return safeMax;
        }
        return safeScore;
    }

    private void publishCompleted(LivePracticeAiGradingRequestedEvent event,
                                  BigDecimal score,
                                  Integer isCorrect,
                                  String feedback) {
        boolean published = aiGradingEventPublisher.publishCompleted(new LivePracticeAiGradingCompletedEvent(
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
        if (!published) {
            throw new AiGradingResultPublishException("AI grading result publish failed");
        }
    }

    private void publishFailure(LivePracticeAiGradingRequestedEvent event, String errorMessage) {
        boolean published = aiGradingEventPublisher.publishCompleted(new LivePracticeAiGradingCompletedEvent(
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
        if (!published) {
            throw new AiGradingResultPublishException("AI grading failure result publish failed");
        }
    }

    private static class AiGradingResultPublishException extends RuntimeException {
        private AiGradingResultPublishException(String message) {
            super(message);
        }
    }
}
