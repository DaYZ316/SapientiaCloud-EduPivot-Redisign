package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.enums.MessageRole;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.util.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationMessageStateService {

    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_COMPLETED = "completed";
    private static final String STATUS_FAILED = "failed";
    private static final String STATUS_TERMINATED = "terminated";
    private static final String STAGE_RECEIVED = "RECEIVED";
    private static final String STAGE_RESPONDED = "RESPONDED";
    private static final String STAGE_FAILED = "FAILED";
    private static final String STAGE_TERMINATED = "TERMINATED";
    private static final String DETAIL_RAW_AI_OUTPUT = "raw_ai_output";

    private final MessageRepository messageRepository;

    public ChatMessage createProcessingMessage(UUID conversationId,
                                               AiAgentMode mode,
                                               String requestId,
                                               GenerationRequest request) {
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generationRequestId", requestId);
        payload.put("generationMode", mode.name());
        payload.put("generationStatus", STATUS_PROCESSING);
        payload.put("generationStage", STAGE_RECEIVED);
        payload.put("generationStartedAt", now);
        payload.put("generationUpdatedAt", now);
        payload.put("generationRequest", generationPayload(request));
        payload.put("generationTrace", List.of(receivedTraceEntry(requestId, mode, now)));

        ChatMessage message = new ChatMessage();
        message.setId(UuidV7Generator.generate());
        message.setConversationId(conversationId);
        message.setRole(MessageRole.ASSISTANT.name());
        message.setContent("");
        message.setMessageType(messageType(mode).name());
        message.setPayload(payload);
        messageRepository.save(message);
        return message;
    }

    public void appendStage(UUID messageId, GenerationStageEvent event) {
        if (messageId == null || event == null) {
            return;
        }
        update(messageId, message -> {
            Map<String, Object> payload = payloadCopy(message.getPayload());
            if (isTerminated(payload)) {
                return false;
            }
            Instant now = Instant.now();
            boolean debugStage = isDebugStage(event);
            String traceKey = debugStage ? "generationDebugTrace" : "generationTrace";
            List<Object> trace = traceCopy(payload.get(traceKey));
            if (!debugStage && !isDuplicateInitialReceived(trace, event)) {
                trace.add(stageTraceEntry(event, trace.size(), now));
            } else if (debugStage && !hasDuplicateEvent(trace, event)) {
                trace.add(stageTraceEntry(event, trace.size(), now));
            }
            payload.put(traceKey, trace);
            payload.put("generationRequestId", event.requestId());
            payload.put("generationMode", event.mode());
            payload.put("generationStage", event.stage());
            payload.put("generationStatus", normalizeStatus(event));
            if (!debugStage) {
                promoteGeneratedQuestions(payload, event);
            }
            payload.put("generationUpdatedAt", now);
            message.setPayload(payload);
            return true;
        });
    }

    public void markCompleted(UUID messageId, String requestId, AiAgentMode mode, AiAgentResult result) {
        if (messageId == null || result == null) {
            return;
        }
        update(messageId, message -> {
            Map<String, Object> payload = payloadCopy(message.getPayload());
            if (isTerminated(payload)) {
                return false;
            }
            Map<String, Object> resultPayload = payloadCopy(result.payload());
            payload.putAll(resultPayload);
            List<Object> trace = traceCopy(payload.get("generationTrace"));
            if (!hasStage(trace, STAGE_RESPONDED)) {
                trace.add(stageTraceEntry(
                        requestId,
                        mode.name(),
                        STAGE_RESPONDED,
                        STATUS_COMPLETED,
                        "生成完成",
                        "生成结果已准备就绪。",
                        Map.of(),
                        trace.size(),
                        Instant.now()));
            }
            payload.put("generationTrace", trace);
            payload.put("generationRequestId", requestId);
            payload.put("generationMode", mode.name());
            payload.put("generationStage", STAGE_RESPONDED);
            payload.put("generationStatus", STATUS_COMPLETED);
            payload.put("generationUpdatedAt", Instant.now());
            message.setContent(result.content());
            message.setMessageType(result.messageType().name());
            message.setPayload(payload);
            return true;
        });
    }

    public void markFailed(UUID messageId, String requestId, AiAgentMode mode, String errorMessage) {
        if (messageId == null) {
            return;
        }
        update(messageId, message -> {
            Map<String, Object> payload = payloadCopy(message.getPayload());
            if (isTerminated(payload)) {
                return false;
            }
            List<Object> trace = traceCopy(payload.get("generationTrace"));
            trace.add(stageTraceEntry(
                    requestId,
                    mode.name(),
                    STAGE_FAILED,
                    STATUS_FAILED,
                    "Generation failed",
                    errorMessage,
                    Map.of("errorMessage", errorMessage),
                    trace.size(),
                    Instant.now()));
            payload.put("generationTrace", trace);
            payload.put("generationRequestId", requestId);
            payload.put("generationMode", mode.name());
            payload.put("generationStage", STAGE_FAILED);
            payload.put("generationStatus", STATUS_FAILED);
            payload.put("generationErrorMessage", errorMessage);
            payload.put("generationUpdatedAt", Instant.now());
            message.setContent(errorMessage == null ? "" : errorMessage);
            if (message.getMessageType() == null || AiMessageType.TEXT.name().equals(message.getMessageType())) {
                message.setMessageType(messageType(mode).name());
            }
            message.setPayload(payload);
            return true;
        });
    }

    public void markTerminated(UUID messageId, String requestId, AiAgentMode mode, String reason) {
        if (messageId == null) {
            return;
        }
        update(messageId, message -> {
            Map<String, Object> payload = payloadCopy(message.getPayload());
            if (!STATUS_PROCESSING.equals(textValue(payload.get("generationStatus")))) {
                return false;
            }
            Instant now = Instant.now();
            String modeName = mode == null ? textValue(payload.get("generationMode")) : mode.name();
            String finalReason = reason == null ? "" : reason;
            List<Object> trace = traceCopy(payload.get("generationTrace"));
            if (!hasStage(trace, STAGE_TERMINATED)) {
                trace.add(stageTraceEntry(
                        requestId,
                        modeName,
                        STAGE_TERMINATED,
                        STATUS_TERMINATED,
                        "任务已终止",
                        finalReason,
                        Map.of("reason", finalReason),
                        trace.size(),
                        now));
            }
            payload.put("generationTrace", trace);
            payload.put("generationRequestId", requestId);
            payload.put("generationMode", modeName);
            payload.put("generationStage", STAGE_TERMINATED);
            payload.put("generationStatus", STATUS_TERMINATED);
            payload.put("generationTerminatedAt", now);
            payload.put("generationErrorMessage", finalReason);
            payload.put("generationUpdatedAt", now);
            if (message.getContent() == null || message.getContent().isBlank()) {
                message.setContent(finalReason);
            }
            if (message.getMessageType() == null || AiMessageType.TEXT.name().equals(message.getMessageType())) {
                message.setMessageType(messageType(modeName).name());
            }
            message.setPayload(payload);
            return true;
        });
    }

    private void update(UUID messageId, GenerationMessageMutator mutator) {
        messageRepository.findById(messageId).ifPresentOrElse(message -> {
            if (mutator.mutate(message)) {
                messageRepository.update(message);
            }
        }, () -> log.warn("Generation message not found messageId={}", messageId));
    }

    private boolean isTerminated(Map<String, Object> payload) {
        return STATUS_TERMINATED.equals(textValue(payload.get("generationStatus")))
                || STAGE_TERMINATED.equals(textValue(payload.get("generationStage")));
    }

    private String textValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Map<String, Object> payloadCopy(Map<String, Object> payload) {
        return payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
    }

    private List<Object> traceCopy(Object value) {
        List<Object> trace = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.stream()
                    .filter(item -> item != null)
                    .forEach(trace::add);
        }
        return trace;
    }

    private boolean isDuplicateInitialReceived(List<Object> trace, GenerationStageEvent event) {
        return STAGE_RECEIVED.equals(event.stage()) && hasStage(trace, STAGE_RECEIVED);
    }

    private boolean hasStage(List<Object> trace, String stage) {
        return trace.stream().anyMatch(entry -> {
            if (entry instanceof Map<?, ?> map) {
                Object value = map.get("stage");
                return stage.equals(value == null ? null : value.toString());
            }
            return false;
        });
    }

    private boolean hasDuplicateEvent(List<Object> trace, GenerationStageEvent event) {
        return trace.stream().anyMatch(entry -> {
            if (!(entry instanceof Map<?, ?> map)) {
                return false;
            }
            return equalsText(map.get("stage"), event.stage())
                    && equalsText(map.get("title"), event.title())
                    && equalsText(map.get("summary"), event.summary())
                    && String.valueOf(map.get("payload")).equals(String.valueOf(event.payload()));
        });
    }

    private boolean isDebugStage(GenerationStageEvent event) {
        Map<String, Object> payload = event.payload();
        Object detailType = payload == null ? null : payload.get("detailType");
        return DETAIL_RAW_AI_OUTPUT.equals(detailType == null ? null : detailType.toString());
    }

    private boolean equalsText(Object left, Object right) {
        return textValue(left).equals(textValue(right));
    }

    private void promoteGeneratedQuestions(Map<String, Object> payload, GenerationStageEvent event) {
        if (!shouldPromoteQuestions(event.stage())) {
            return;
        }
        Map<String, Object> eventPayload = event.payload();
        Object questions = eventPayload == null ? null : eventPayload.get("questions");
        if (questions instanceof List<?> list) {
            payload.put("questions", Boolean.TRUE.equals(eventPayload.get("questionDelta"))
                    ? appendQuestions(payload.get("questions"), list)
                    : list);
        }
    }

    private List<Object> appendQuestions(Object current, List<?> delta) {
        List<Object> questions = new ArrayList<>();
        if (current instanceof List<?> list) {
            questions.addAll(list);
        }
        questions.addAll(delta);
        return questions;
    }

    private boolean shouldPromoteQuestions(String stage) {
        return "GENERATED".equals(stage) || "REPAIRED".equals(stage) || "ASSEMBLED".equals(stage);
    }

    private String normalizeStatus(GenerationStageEvent event) {
        String status = event.status();
        if (STAGE_FAILED.equals(event.stage()) || "error".equals(status)) {
            return STATUS_FAILED;
        }
        return status == null || status.isBlank() ? STATUS_PROCESSING : status;
    }

    private Map<String, Object> receivedTraceEntry(String requestId, AiAgentMode mode, Instant timestamp) {
        return stageTraceEntry(
                requestId,
                mode.name(),
                STAGE_RECEIVED,
                STATUS_PROCESSING,
                "生成任务已接收",
                "生成任务已进入处理队列。",
                Map.of(),
                0,
                timestamp);
    }

    private Map<String, Object> stageTraceEntry(GenerationStageEvent event, int index, Instant timestamp) {
        return stageTraceEntry(
                event.requestId(),
                event.mode(),
                event.stage(),
                normalizeStatus(event),
                event.title(),
                event.summary(),
                event.payload(),
                index,
                timestamp);
    }

    private Map<String, Object> stageTraceEntry(String requestId,
                                               String mode,
                                               String stage,
                                               String status,
                                               String title,
                                               String summary,
                                               Map<String, Object> payload,
                                               int index,
                                               Instant timestamp) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("entryId", (requestId == null ? "generation" : requestId) + "-" + stage + "-" + index);
        entry.put("stage", stage);
        entry.put("source", "question-generation");
        entry.put("detailType", detailType(stage, payload));
        entry.put("title", title);
        entry.put("summary", summary);
        entry.put("payload", payload == null ? Map.of() : payload);
        entry.put("timestamp", timestamp);
        entry.put("status", status);
        entry.put("mode", mode);
        return entry;
    }

    private String detailType(String stage, Map<String, Object> payload) {
        Object detailType = payload == null ? null : payload.get("detailType");
        if (detailType != null && !detailType.toString().isBlank()) {
            return detailType.toString();
        }
        return stage == null ? null : stage.toLowerCase();
    }

    private AiMessageType messageType(AiAgentMode mode) {
        if (mode == AiAgentMode.PAPER) {
            return AiMessageType.PAPER;
        }
        if (mode == AiAgentMode.QUESTION) {
            return AiMessageType.QUESTION_SET;
        }
        return AiMessageType.TEXT;
    }

    private AiMessageType messageType(String mode) {
        if (AiAgentMode.PAPER.name().equals(mode)) {
            return AiMessageType.PAPER;
        }
        if (AiAgentMode.QUESTION.name().equals(mode)) {
            return AiMessageType.QUESTION_SET;
        }
        return AiMessageType.TEXT;
    }

    private Map<String, Object> generationPayload(GenerationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (request == null) {
            return payload;
        }
        payload.put("questionBankId", request.questionBankId() == null ? null : request.questionBankId().toString());
        payload.put("questionCount", request.questionCount());
        payload.put("questionType", request.questionType());
        payload.put("difficulty", request.difficulty());
        payload.put("scorePerQuestion", request.scorePerQuestion());
        payload.put("totalScore", request.totalScore());
        payload.put("totalEstimatedTime", request.totalEstimatedTime());
        payload.put("paperName", request.paperName());
        payload.put("paperType", request.paperType());
        payload.put("requirement", request.requirement());
        payload.put("chapterIds", request.chapterIds());
        payload.put("knowledgePoints", request.knowledgePoints());
        payload.put("abilityGoals", request.abilityGoals());
        return payload;
    }

    @FunctionalInterface
    private interface GenerationMessageMutator {
        boolean mutate(ChatMessage message);
    }

}
