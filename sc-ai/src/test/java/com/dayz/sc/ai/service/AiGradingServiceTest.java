package com.dayz.sc.ai.service;

import com.dayz.sc.ai.event.AiGradingEventPublisher;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingCompletedEvent;
import com.dayz.sc.common.events.ai.LivePracticeAiGradingRequestedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGradingServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private AiRuntimeGuard aiRuntimeGuard;

    @Mock
    private AiGradingEventPublisher aiGradingEventPublisher;

    @Mock
    private AiProviderCallGuard aiProviderCallGuard;

    @Captor
    private ArgumentCaptor<LivePracticeAiGradingCompletedEvent> completedEventCaptor;

    @Captor
    private ArgumentCaptor<String> promptCaptor;

    private AiGradingService aiGradingService;

    @BeforeEach
    void setUp() {
        aiGradingService = new AiGradingService(
                chatClient,
                aiRuntimeGuard,
                aiGradingEventPublisher,
                new ObjectMapper(),
                aiProviderCallGuard
        );
        when(aiRuntimeGuard.isConfigured()).thenReturn(true);
    }

    @Test
    void grade_shouldPublishCompletedForValidJson() {
        stubModelResponse("{\"score\":8,\"isCorrect\":false,\"feedback\":\"要点较完整\"}");

        aiGradingService.grade(event());

        verify(aiGradingEventPublisher).publishCompleted(completedEventCaptor.capture());
        LivePracticeAiGradingCompletedEvent completed = completedEventCaptor.getValue();
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.earnedScore()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(completed.feedback()).isEqualTo("要点较完整");
    }

    @Test
    void grade_shouldParseMarkdownJsonAndClampScore() {
        stubModelResponse("```json\n{\"score\":99,\"isCorrect\":true,\"feedback\":\"满分\"}\n```");

        aiGradingService.grade(event());

        verify(aiGradingEventPublisher).publishCompleted(completedEventCaptor.capture());
        assertThat(completedEventCaptor.getValue().earnedScore()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void grade_shouldPublishFailureForInvalidJson() {
        stubModelResponse("not json");

        aiGradingService.grade(event());

        verify(aiGradingEventPublisher).publishCompleted(completedEventCaptor.capture());
        assertThat(completedEventCaptor.getValue().status()).isEqualTo("FAILED");
        assertThat(completedEventCaptor.getValue().errorMessage()).contains("AI grading failed");
    }

    @Test
    void grade_shouldPublishFailureWhenRuntimeIsMissing() {
        when(aiRuntimeGuard.isConfigured()).thenReturn(false);
        when(aiRuntimeGuard.missingKeyMessage()).thenReturn("missing key");

        aiGradingService.grade(event());

        verify(aiGradingEventPublisher).publishCompleted(completedEventCaptor.capture());
        assertThat(completedEventCaptor.getValue().status()).isEqualTo("FAILED");
        assertThat(completedEventCaptor.getValue().errorMessage()).isEqualTo("missing key");
    }

    @Test
    void grade_shouldIncludeRequirementAndAnswerInPrompt() {
        stubModelResponse("{\"score\":8,\"isCorrect\":false,\"feedback\":\"要点较完整\"}");

        aiGradingService.grade(event());

        verify(chatClient.prompt()).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("按封装定义和作用给分", "封装隐藏内部实现", "学生答案");
    }

    private void stubModelResponse(String response) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
        when(aiProviderCallGuard.call(any())).thenAnswer(invocation -> invocation.getArgument(0, java.util.function.Supplier.class).get());
    }

    private LivePracticeAiGradingRequestedEvent event() {
        return new LivePracticeAiGradingRequestedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "解释封装",
                "请解释封装。",
                BigDecimal.TEN,
                List.of(new LivePracticeAiGradingRequestedEvent.AnswerReference(
                        "封装隐藏内部实现并暴露必要接口。",
                        null,
                        BigDecimal.TEN,
                        1
                )),
                "封装隐藏内部实现。",
                "按封装定义和作用给分",
                "LIVE_PRACTICE_AI_GRADING_REQUESTED",
                Instant.now(),
                "sc-course"
        );
    }
}
