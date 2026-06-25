package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DashScopeAsrClient {

    private static final String MISSING_ASR_MESSAGE = "AI summary is temporarily unavailable.";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final DashScopeAsrResultParser resultParser;
    private final HttpClient httpClient;

    public DashScopeAsrClient(AiProperties aiProperties,
                              ObjectMapper objectMapper,
                              DashScopeAsrResultParser resultParser) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.resultParser = resultParser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties().getApiKey())
                && StringUtils.hasText(properties().getWebsocketUrl());
    }

    public LiveSummaryAsrStream connect(AsrListener listener) {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, MISSING_ASR_MESSAGE);
        }
        UUID taskId = UuidV7Generator.generate();
        CompletableFuture<WebSocket> taskStartedFuture = new CompletableFuture<>();
        CompletableFuture<WebSocket> taskFinishedFuture = new CompletableFuture<>();
        AsrWebSocketListener webSocketListener = new AsrWebSocketListener(
                listener, taskStartedFuture, taskFinishedFuture);
        CompletableFuture<WebSocket> socketFuture = httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + properties().getApiKey())
                .buildAsync(URI.create(properties().getWebsocketUrl()), webSocketListener)
                .thenApply(webSocket -> webSocket);
        socketFuture.exceptionally(error -> {
            taskStartedFuture.completeExceptionally(error);
            taskFinishedFuture.completeExceptionally(error);
            listener.onError(error);
            return null;
        });
        socketFuture.thenAccept(webSocket -> webSocket.sendText(runTaskMessage(taskId), true)
                .exceptionally(error -> {
                    listener.onError(error);
                    taskStartedFuture.completeExceptionally(error);
                    taskFinishedFuture.completeExceptionally(error);
                    return null;
                }));
        return new LiveSummaryAsrStream(socketFuture, taskStartedFuture, taskFinishedFuture, finishTaskMessage(taskId));
    }

    private AiProperties.LiveSummary.Asr properties() {
        return aiProperties.getLiveSummary().getAsr();
    }

    private String runTaskMessage(UUID taskId) {
        return toJson(Map.of(
                "header", Map.of(
                        "action", "run-task",
                        "task_id", taskId.toString(),
                        "streaming", "duplex"),
                "payload", Map.of(
                        "task_group", "audio",
                        "task", "asr",
                        "function", "recognition",
                        "model", properties().getModel(),
                        "parameters", Map.of(
                                "format", properties().getFormat(),
                                "sample_rate", properties().getSampleRate()))));
    }

    private String finishTaskMessage(UUID taskId) {
        return toJson(Map.of(
                "header", Map.of(
                        "action", "finish-task",
                        "task_id", taskId.toString(),
                        "streaming", "duplex"),
                "payload", Map.of()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public interface AsrListener {
        void onTranscript(AsrTranscript transcript);

        void onError(Throwable error);
    }

    public record AsrTranscript(String text, boolean sentenceEnd, Integer beginTimeMs, Integer endTimeMs) {
    }

    public static class LiveSummaryAsrStream implements AutoCloseable {

        private final CompletableFuture<WebSocket> socketFuture;
        private final CompletableFuture<WebSocket> taskStartedFuture;
        private final CompletableFuture<WebSocket> taskFinishedFuture;
        private CompletableFuture<WebSocket> sendChain;
        private final String finishTaskMessage;

        private LiveSummaryAsrStream(CompletableFuture<WebSocket> socketFuture,
                                     CompletableFuture<WebSocket> taskStartedFuture,
                                     CompletableFuture<WebSocket> taskFinishedFuture,
                                     String finishTaskMessage) {
            this.socketFuture = socketFuture;
            this.taskStartedFuture = taskStartedFuture;
            this.taskFinishedFuture = taskFinishedFuture;
            this.sendChain = socketFuture.thenApply(webSocket -> webSocket);
            this.finishTaskMessage = finishTaskMessage;
        }

        public synchronized void sendAudio(byte[] audio) {
            if (audio == null || audio.length == 0) {
                return;
            }
            byte[] chunk = audio.clone();
            sendChain = sendChain.thenCompose(webSocket -> taskStartedFuture.thenCompose(ignored ->
                    webSocket.sendBinary(ByteBuffer.wrap(chunk), true)));
        }

        @Override
        public synchronized void close() {
            if (!isTaskStarted()) {
                socketFuture.thenAccept(WebSocket::abort);
                return;
            }
            sendChain = sendChain.thenCompose(webSocket -> webSocket.sendText(finishTaskMessage, true))
                    .thenCompose(webSocket -> taskFinishedFuture.completeOnTimeout(webSocket, 2, TimeUnit.SECONDS))
                    .thenCompose(webSocket -> webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                            .thenApply(ignored -> webSocket))
                    .exceptionally(error -> {
                        socketFuture.thenAccept(WebSocket::abort);
                        return null;
                    });
        }

        private boolean isTaskStarted() {
            return taskStartedFuture.isDone()
                    && !taskStartedFuture.isCompletedExceptionally()
                    && !taskStartedFuture.isCancelled();
        }
    }

    private class AsrWebSocketListener implements WebSocket.Listener {

        private final AsrListener listener;
        private final CompletableFuture<WebSocket> taskStartedFuture;
        private final CompletableFuture<WebSocket> taskFinishedFuture;
        private final StringBuilder textBuffer = new StringBuilder();

        private AsrWebSocketListener(AsrListener listener,
                                     CompletableFuture<WebSocket> taskStartedFuture,
                                     CompletableFuture<WebSocket> taskFinishedFuture) {
            this.listener = listener;
            this.taskStartedFuture = taskStartedFuture;
            this.taskFinishedFuture = taskFinishedFuture;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String message = textBuffer.toString();
                textBuffer.setLength(0);
                handleMessage(webSocket, message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("DashScope ASR websocket failed", error);
            taskStartedFuture.completeExceptionally(error);
            taskFinishedFuture.completeExceptionally(error);
            listener.onError(error);
        }

        private void handleMessage(WebSocket webSocket, String message) {
            String event = resultParser.extractEvent(message);
            if ("task-started".equals(event)) {
                taskStartedFuture.complete(webSocket);
                return;
            }
            if ("task-finished".equals(event)) {
                taskFinishedFuture.complete(webSocket);
                return;
            }
            if ("task-failed".equals(event)) {
                String errorMessage = resultParser.extractErrorMessage(message);
                RuntimeException failure = new IllegalStateException(
                        StringUtils.hasText(errorMessage) ? errorMessage : "DashScope ASR task failed");
                taskStartedFuture.completeExceptionally(failure);
                taskFinishedFuture.completeExceptionally(failure);
                listener.onError(failure);
                webSocket.abort();
                return;
            }
            if (!"result-generated".equals(event)) {
                return;
            }
            resultParser.parse(message).forEach(listener::onTranscript);
        }
    }
}
