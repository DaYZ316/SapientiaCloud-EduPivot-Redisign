package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.enums.MessageRole;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class GenerationMessageStateServiceTest {

    @Test
    void createProcessingMessageShouldPersistGenerationPlaceholder() {
        MessageRepository repository = mock(MessageRepository.class);
        GenerationMessageStateService service = new GenerationMessageStateService(repository);
        GenerationRequest request = new GenerationRequest(
                UUID.randomUUID(),
                5,
                0,
                2,
                BigDecimal.valueOf(4),
                null,
                null,
                null,
                null,
                "cover HashMap",
                null,
                List.of("HashMap"),
                null);

        ChatMessage message = service.createProcessingMessage(
                UUID.randomUUID(),
                AiAgentMode.QUESTION,
                "request-1",
                request);

        assertThat(message.getRole()).isEqualTo(MessageRole.ASSISTANT.name());
        assertThat(message.getMessageType()).isEqualTo(AiMessageType.QUESTION_SET.name());
        assertThat(message.getPayload())
                .containsEntry("generationRequestId", "request-1")
                .containsEntry("generationMode", "QUESTION")
                .containsEntry("generationStatus", "processing")
                .containsEntry("generationStage", "RECEIVED");
        Map<?, ?> generationRequest = (Map<?, ?>) message.getPayload().get("generationRequest");
        assertThat(generationRequest.get("questionCount")).isEqualTo(5);
        assertThat(generationRequest.get("requirement")).isEqualTo("cover HashMap");
        verify(repository).save(message);
    }

    @Test
    void markCompletedShouldUpdateSameMessageWithResultPayload() {
        UUID messageId = UUID.randomUUID();
        MessageRepository repository = mock(MessageRepository.class);
        ChatMessage message = persistedMessage(messageId);
        when(repository.findById(messageId)).thenReturn(Optional.of(message));
        GenerationMessageStateService service = new GenerationMessageStateService(repository);

        service.markCompleted(
                messageId,
                "request-1",
                AiAgentMode.PAPER,
                new AiAgentResult("paper content", AiMessageType.PAPER, Map.of("schemaVersion", 2)));

        verify(repository).update(argThat(updated ->
                messageId.equals(updated.getId())
                        && "paper content".equals(updated.getContent())
                        && AiMessageType.PAPER.name().equals(updated.getMessageType())
                        && "completed".equals(updated.getPayload().get("generationStatus"))
                        && "RESPONDED".equals(updated.getPayload().get("generationStage"))
                        && Integer.valueOf(2).equals(updated.getPayload().get("schemaVersion"))));
    }

    @Test
    void markFailedShouldKeepMessageAsRecoverableFailedArtifact() {
        UUID messageId = UUID.randomUUID();
        MessageRepository repository = mock(MessageRepository.class);
        when(repository.findById(messageId)).thenReturn(Optional.of(persistedMessage(messageId)));
        GenerationMessageStateService service = new GenerationMessageStateService(repository);

        service.markFailed(messageId, "request-1", AiAgentMode.QUESTION, "failed");

        verify(repository).update(argThat(updated ->
                messageId.equals(updated.getId())
                        && "failed".equals(updated.getContent())
                        && "failed".equals(updated.getPayload().get("generationStatus"))
                        && "FAILED".equals(updated.getPayload().get("generationStage"))
                        && "failed".equals(updated.getPayload().get("generationErrorMessage"))));
    }

    @Test
    void appendStageShouldPersistProgressTrace() {
        UUID messageId = UUID.randomUUID();
        MessageRepository repository = mock(MessageRepository.class);
        when(repository.findById(messageId)).thenReturn(Optional.of(persistedMessage(messageId)));
        GenerationMessageStateService service = new GenerationMessageStateService(repository);

        service.appendStage(messageId, GenerationStageEvent.of(
                "request-1",
                "QUESTION",
                "PLANNED",
                "processing",
                "planned",
                "summary",
                Map.of("questionCount", 5)));

        verify(repository).update(argThat(updated ->
                "PLANNED".equals(updated.getPayload().get("generationStage"))
                        && "processing".equals(updated.getPayload().get("generationStatus"))
                        && ((List<?>) updated.getPayload().get("generationTrace")).size() == 2));
    }

    @Test
    void appendStageShouldPersistRawAiOutputToDebugTrace() {
        UUID messageId = UUID.randomUUID();
        MessageRepository repository = mock(MessageRepository.class);
        ChatMessage message = persistedMessage(messageId);
        when(repository.findById(messageId)).thenReturn(Optional.of(message));
        GenerationMessageStateService service = new GenerationMessageStateService(repository);

        service.appendStage(messageId, GenerationStageEvent.of(
                "request-1",
                "QUESTION",
                "GENERATED",
                "processing",
                "raw model output",
                "raw output returned",
                Map.of(
                        "detailType", "raw_ai_output",
                        "callType", "section_generation",
                        "rawOutput", "{\"questions\":[]}",
                        "questionDelta", true,
                        "questions", List.of(Map.of("questionTitle", "debug only")))));

        verify(repository).update(argThat(updated -> {
            Map<String, Object> payload = updated.getPayload();
            List<?> debugTrace = (List<?>) payload.get("generationDebugTrace");
            List<?> trace = (List<?>) payload.get("generationTrace");
            Map<?, ?> debugEntry = (Map<?, ?>) debugTrace.getFirst();
            return "GENERATED".equals(payload.get("generationStage"))
                    && "processing".equals(payload.get("generationStatus"))
                    && trace.size() == 1
                    && debugTrace.size() == 1
                    && "raw_ai_output".equals(debugEntry.get("detailType"))
                    && "section_generation".equals(((Map<?, ?>) debugEntry.get("payload")).get("callType"))
                    && !payload.containsKey("questions");
        }));
    }

    @Test
    void appendStageShouldAccumulateGeneratedQuestionDeltas() {
        UUID messageId = UUID.randomUUID();
        MessageRepository repository = mock(MessageRepository.class);
        ChatMessage message = persistedMessage(messageId);
        when(repository.findById(messageId)).thenReturn(Optional.of(message));
        GenerationMessageStateService service = new GenerationMessageStateService(repository);

        service.appendStage(messageId, generatedQuestionStage("request-1", "HashMap load factor"));
        service.appendStage(messageId, generatedQuestionStage("request-1", "ConcurrentHashMap segment"));

        List<String> questionTitles = ((List<?>) message.getPayload().get("questions")).stream()
                .map(item -> String.valueOf(((Map<?, ?>) item).get("questionTitle")))
                .toList();
        assertThat(questionTitles)
                .containsExactly("HashMap load factor", "ConcurrentHashMap segment");
        verify(repository, org.mockito.Mockito.times(2)).update(any());
    }

    @Test
    void terminatedMessageShouldNotBeOverwrittenByLateCompletion() {
        UUID messageId = UUID.randomUUID();
        MessageRepository repository = mock(MessageRepository.class);
        ChatMessage message = persistedMessage(messageId);
        when(repository.findById(messageId)).thenReturn(Optional.of(message));
        GenerationMessageStateService service = new GenerationMessageStateService(repository);

        service.markTerminated(messageId, "request-1", AiAgentMode.PAPER, "stopped");
        service.markCompleted(
                messageId,
                "request-1",
                AiAgentMode.PAPER,
                new AiAgentResult("late result", AiMessageType.PAPER, Map.of("schemaVersion", 2)));

        assertThat(message.getContent()).isEqualTo("stopped");
        assertThat(message.getPayload())
                .containsEntry("generationStatus", "terminated")
                .containsEntry("generationStage", "TERMINATED");
        verify(repository, times(1)).update(any());
    }

    private ChatMessage persistedMessage(UUID messageId) {
        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setConversationId(UUID.randomUUID());
        message.setRole(MessageRole.ASSISTANT.name());
        message.setContent("");
        message.setMessageType(AiMessageType.QUESTION_SET.name());
        message.setPayload(Map.of(
                "generationStatus", "processing",
                "generationStage", "RECEIVED",
                "generationTrace", List.of(Map.of("stage", "RECEIVED"))));
        return message;
    }

    private GenerationStageEvent generatedQuestionStage(String requestId, String questionTitle) {
        return GenerationStageEvent.of(
                requestId,
                "PAPER",
                "GENERATED",
                "processing",
                "drafting",
                "summary",
                Map.of(
                        "questionDelta", true,
                        "questions", List.of(Map.of("questionTitle", questionTitle))));
    }
}
