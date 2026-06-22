package com.dayz.sc.ai.event;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.service.AiAgentService;
import com.dayz.sc.ai.service.GenerationMessageStateService;
import com.dayz.sc.ai.service.QuestionGenerationKafkaBridge;
import com.dayz.sc.common.events.ai.QuestionGenerationRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestionGenerationWorkerTest {

    @Test
    void onRequestedShouldPublishReceivedBeforeRunningGeneration() {
        AiAgentService aiAgentService = mock(AiAgentService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        GenerationMessageStateService generationMessageStateService = mock(GenerationMessageStateService.class);
        QuestionGenerationWorker worker = new QuestionGenerationWorker(
                aiAgentService,
                kafkaBridge,
                generationMessageStateService);
        String requestId = "request-1";
        QuestionGenerationRequestedEvent event = new QuestionGenerationRequestedEvent(
                UUID.randomUUID(),
                requestId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                "generate paper",
                AiAgentMode.PAPER.name(),
                Map.of("questionCount", 20));
        when(aiAgentService.runGeneration(any(ChatRequest.class), any(), any(), any(), any(), eq(requestId)))
                .thenReturn(new AiAgentResult("done", AiMessageType.PAPER, Map.of()));

        worker.onRequested(event, null);

        InOrder inOrder = inOrder(kafkaBridge, aiAgentService);
        inOrder.verify(kafkaBridge).publishStage(eq(requestId), any(GenerationStageEvent.class));
        inOrder.verify(aiAgentService).runGeneration(any(ChatRequest.class), any(), any(), any(), any(), eq(requestId));
    }

    @Test
    void onRequestedShouldPublishReceivedStagePayload() {
        AiAgentService aiAgentService = mock(AiAgentService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        GenerationMessageStateService generationMessageStateService = mock(GenerationMessageStateService.class);
        QuestionGenerationWorker worker = new QuestionGenerationWorker(
                aiAgentService,
                kafkaBridge,
                generationMessageStateService);
        String requestId = "request-1";
        QuestionGenerationRequestedEvent event = new QuestionGenerationRequestedEvent(
                UUID.randomUUID(),
                requestId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                "generate questions",
                AiAgentMode.QUESTION.name(),
                Map.of("questionCount", 5));
        when(aiAgentService.runGeneration(any(ChatRequest.class), any(), any(), any(), any(), eq(requestId)))
                .thenReturn(new AiAgentResult("done", AiMessageType.QUESTION_SET, Map.of()));

        worker.onRequested(event, null);

        ArgumentCaptor<GenerationStageEvent> stageCaptor = ArgumentCaptor.forClass(GenerationStageEvent.class);
        InOrder inOrder = inOrder(kafkaBridge, aiAgentService);
        inOrder.verify(kafkaBridge).publishStage(eq(requestId), stageCaptor.capture());
        inOrder.verify(aiAgentService).runGeneration(any(ChatRequest.class), any(), any(), any(), any(), eq(requestId));

        GenerationStageEvent stage = stageCaptor.getValue();
        assertThat(stage).isNotNull();
        assertThat(stage.requestId()).isEqualTo(requestId);
        assertThat(stage.mode()).isEqualTo(AiAgentMode.QUESTION.name());
        assertThat(stage.stage()).isEqualTo("RECEIVED");
        assertThat(stage.status()).isEqualTo("processing");
        assertThat(stage.payload()).containsEntry("queued", true);
    }
}
