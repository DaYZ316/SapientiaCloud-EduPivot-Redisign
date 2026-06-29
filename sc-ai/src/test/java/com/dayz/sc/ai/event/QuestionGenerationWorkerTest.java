package com.dayz.sc.ai.event;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.service.AiAgentService;
import com.dayz.sc.ai.service.GenerationCancelledException;
import com.dayz.sc.ai.service.GenerationMessageStateService;
import com.dayz.sc.ai.service.QuestionGenerationCancellationService;
import com.dayz.sc.ai.service.QuestionGenerationKafkaBridge;
import com.dayz.sc.common.events.ai.QuestionGenerationRequestedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QuestionGenerationWorkerTest {

    @Test
    void onRequestedShouldPublishReceivedBeforeRunningGeneration() {
        AiAgentService aiAgentService = mock(AiAgentService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        GenerationMessageStateService generationMessageStateService = mock(GenerationMessageStateService.class);
        QuestionGenerationCancellationService cancellationService = mock(QuestionGenerationCancellationService.class);
        QuestionGenerationWorker worker = worker(
                aiAgentService, kafkaBridge, generationMessageStateService, cancellationService);
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
        QuestionGenerationCancellationService cancellationService = mock(QuestionGenerationCancellationService.class);
        QuestionGenerationWorker worker = worker(
                aiAgentService, kafkaBridge, generationMessageStateService, cancellationService);
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

    @Test
    void onRequestedShouldCancelRunningFutureWithoutPublishingFailure() throws Exception {
        AiAgentService aiAgentService = mock(AiAgentService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        GenerationMessageStateService generationMessageStateService = mock(GenerationMessageStateService.class);
        QuestionGenerationCancellationService cancellationService = mock(QuestionGenerationCancellationService.class);
        QuestionGenerationTaskRegistry registry = new QuestionGenerationTaskRegistry();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        QuestionGenerationWorker worker = new QuestionGenerationWorker(
                aiAgentService,
                kafkaBridge,
                generationMessageStateService,
                cancellationService,
                registry,
                command -> new Thread(command, "test-question-generation").start());
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
                .thenAnswer(invocation -> {
                    started.countDown();
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(30));
                    } catch (InterruptedException exception) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                        throw new GenerationCancelledException(requestId);
                    }
                    return new AiAgentResult("done", AiMessageType.QUESTION_SET, Map.of());
                });

        worker.onRequested(event, null);
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.cancel(requestId)).isTrue();

        assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(100);
        verify(kafkaBridge, never()).publishFailure(eq(requestId), any());
        verify(generationMessageStateService, never()).markFailed(any(), any(), any(), any());
        verify(kafkaBridge, atLeastOnce()).cancel(requestId, AiAgentMode.QUESTION);
    }

    private QuestionGenerationWorker worker(AiAgentService aiAgentService,
                                            QuestionGenerationKafkaBridge kafkaBridge,
                                            GenerationMessageStateService generationMessageStateService,
                                            QuestionGenerationCancellationService cancellationService) {
        return new QuestionGenerationWorker(
                aiAgentService,
                kafkaBridge,
                generationMessageStateService,
                cancellationService,
                new QuestionGenerationTaskRegistry(),
                Runnable::run);
    }
}
