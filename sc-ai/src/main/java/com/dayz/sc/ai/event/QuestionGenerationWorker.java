package com.dayz.sc.ai.event;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.service.AiAgentService;
import com.dayz.sc.ai.service.GenerationCancelledException;
import com.dayz.sc.ai.service.GenerationMessageStateService;
import com.dayz.sc.ai.service.QuestionGenerationCancellationService;
import com.dayz.sc.ai.service.QuestionGenerationKafkaBridge;
import com.dayz.sc.common.events.ai.QuestionGenerationRequestedEvent;
import com.dayz.sc.common.events.config.KafkaTopicConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

/**
 * QuestionGenerationWorker.
 *
 * @author DaYZ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionGenerationWorker {

    private final AiAgentService aiAgentService;
    private final QuestionGenerationKafkaBridge kafkaBridge;
    private final GenerationMessageStateService generationMessageStateService;
    private final QuestionGenerationCancellationService cancellationService;
    private final QuestionGenerationTaskRegistry taskRegistry;
    private final TaskExecutor questionGenerationTaskExecutor;

    @KafkaListener(topics = KafkaTopicConstants.QUESTION_GENERATION_REQUESTS, groupId = "sc-ai-question-generation-worker")
    public void onRequested(QuestionGenerationRequestedEvent event, Acknowledgment acknowledgment) {
        if (event == null || event.requestId() == null) {
            acknowledge(acknowledgment);
            return;
        }
        FutureTask<Void> task = new FutureTask<>(() -> {
            process(event);
            return null;
        });
        taskRegistry.register(event.requestId(), task);
        try {
            questionGenerationTaskExecutor.execute(() -> {
                try {
                    task.run();
                } finally {
                    taskRegistry.remove(event.requestId());
                    acknowledge(acknowledgment);
                }
            });
        } catch (RejectedExecutionException exception) {
            taskRegistry.remove(event.requestId());
            fail(event, exception);
            acknowledge(acknowledgment);
        }
    }

    private void process(QuestionGenerationRequestedEvent event) {
        try {
            AiAgentMode mode = AiAgentMode.resolve(event.mode(), event.message());
            cancellationService.throwIfCancelled(event.requestId());
            publishReceived(event, mode);
            cancellationService.throwIfCancelled(event.requestId());
            ChatRequest request = new ChatRequest(
                    event.conversationId(),
                    event.message(),
                    mode.name(),
                    event.courseId(),
                    generationRequest(event.generation()));
            AiAgentResult result = aiAgentService.runGeneration(
                    request,
                    event.userId(),
                    event.role(),
                    stage -> publishStage(event, stage),
                    search -> kafkaBridge.publishAgentSearch(event.requestId(), search),
                    event.requestId());
            cancellationService.throwIfCancelled(event.requestId());
            generationMessageStateService.markCompleted(event.assistantMessageId(), event.requestId(), mode, result);
            kafkaBridge.publishResponse(event.requestId(), result);
        } catch (GenerationCancelledException exception) {
            log.info("Question generation worker cancelled requestId={}", event.requestId());
            kafkaBridge.cancel(event.requestId(), AiAgentMode.resolve(event.mode(), event.message()));
        } catch (RuntimeException exception) {
            fail(event, exception);
        }
    }

    private void fail(QuestionGenerationRequestedEvent event, RuntimeException exception) {
        log.error("Question generation worker failed requestId={}", event.requestId(), exception);
        AiAgentMode mode = AiAgentMode.resolve(event.mode(), event.message());
        String errorMessage = "题目生成失败，请稍后重试。";
        generationMessageStateService.markFailed(event.assistantMessageId(), event.requestId(), mode, errorMessage);
        kafkaBridge.publishFailure(event.requestId(), errorMessage);
    }

    private void publishReceived(QuestionGenerationRequestedEvent event, AiAgentMode mode) {
        publishStage(event, GenerationStageEvent.of(
                event.requestId(),
                mode.name(),
                "RECEIVED",
                "processing",
                mode == AiAgentMode.PAPER ? "试卷生成任务已接收" : "题目生成任务已接收",
                "生成任务已进入后台队列，正在准备课程资料。",
                Map.of("queued", true)));
    }

    private void publishStage(QuestionGenerationRequestedEvent event, GenerationStageEvent stage) {
        cancellationService.throwIfCancelled(event.requestId());
        generationMessageStateService.appendStage(event.assistantMessageId(), stage);
        kafkaBridge.publishStage(event.requestId(), stage);
    }

    private GenerationRequest generationRequest(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        return new GenerationRequest(
                uuid(payload.get("questionBankId")),
                integer(payload.get("questionCount")),
                integer(payload.get("questionType")),
                integer(payload.get("difficulty")),
                decimal(payload.get("scorePerQuestion")),
                decimal(payload.get("totalScore")),
                integer(payload.get("totalEstimatedTime")),
                text(payload.get("paperName")),
                text(payload.get("paperType")),
                text(payload.get("requirement")),
                uuidList(payload.get("chapterIds")),
                stringList(payload.get("knowledgePoints")),
                stringList(payload.get("abilityGoals")));
    }

    private UUID uuid(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return UUID.fromString(value.toString());
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return new BigDecimal(value.toString());
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private List<UUID> uuidList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .map(this::uuid)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(Object::toString)
                .toList();
    }

    private void acknowledge(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
