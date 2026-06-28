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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * DashScopeAsrClient.
 *
 * @author DaYZ
 */
@Component
@Slf4j
public class DashScopeAsrClient {

    private static final String MISSING_ASR_MESSAGE = "AI summary is temporarily unavailable.";
    private static final String EVENT_TASK_STARTED = "task-started";
    private static final String EVENT_TASK_FINISHED = "task-finished";
    private static final String EVENT_TASK_FAILED = "task-failed";
    private static final String EVENT_RESULT_GENERATED = "result-generated";
    private static final String EVENT_SESSION_UPDATED = "session.updated";
    private static final String EVENT_SESSION_FINISHED = "session.finished";
    private static final String EVENT_ERROR = "error";
    private static final String EVENT_QWEN_TRANSCRIPTION_FAILED = "conversation.item.input_audio_transcription.failed";
    private static final String QWEN_ASR_FLASH_MODEL = "qwen3-asr-flash";
    private static final String QWEN_ASR_FLASH_REALTIME_MODEL = "qwen3-asr-flash-realtime";

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
        AsrProtocol protocol = protocol();
        UUID taskId = UuidV7Generator.generate();
        CompletableFuture<WebSocket> taskStartedFuture = new CompletableFuture<>();
        CompletableFuture<WebSocket> taskFinishedFuture = new CompletableFuture<>();
        AsrWebSocketListener webSocketListener = new AsrWebSocketListener(
                listener, taskStartedFuture, taskFinishedFuture, protocol);
        CompletableFuture<WebSocket> socketFuture = httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + properties().getApiKey())
                .buildAsync(connectionUri(protocol), webSocketListener)
                .thenApply(webSocket -> webSocket);
        socketFuture.exceptionally(error -> {
            taskStartedFuture.completeExceptionally(error);
            taskFinishedFuture.completeExceptionally(error);
            listener.onError(error);
            return null;
        });
        socketFuture.thenAccept(webSocket -> webSocket.sendText(startMessage(protocol, taskId), true)
                .exceptionally(error -> {
                    listener.onError(error);
                    taskStartedFuture.completeExceptionally(error);
                    taskFinishedFuture.completeExceptionally(error);
                    return null;
                }));
        Function<byte[], String> audioMessageFactory = protocol == AsrProtocol.QWEN_REALTIME
                ? this::qwenInputAudioAppendMessage
                : null;
        return new LiveSummaryAsrStream(
                socketFuture,
                taskStartedFuture,
                taskFinishedFuture,
                finishMessage(protocol, taskId),
                audioMessageFactory);
    }

    private AiProperties.LiveSummary.Asr properties() {
        return aiProperties.getLiveSummary().getAsr();
    }

    private AsrProtocol protocol() {
        return isQwenRealtimeModel(resolvedModel()) ? AsrProtocol.QWEN_REALTIME : AsrProtocol.PARAFORMER;
    }

    private boolean isQwenRealtimeModel(String model) {
        return model.toLowerCase(Locale.ROOT).startsWith(QWEN_ASR_FLASH_REALTIME_MODEL);
    }

    private String resolvedModel() {
        String model = properties().getModel();
        if (!StringUtils.hasText(model)) {
            return QWEN_ASR_FLASH_REALTIME_MODEL;
        }
        String normalized = model.strip().toLowerCase(Locale.ROOT);
        if (QWEN_ASR_FLASH_MODEL.equals(normalized)) {
            return QWEN_ASR_FLASH_REALTIME_MODEL;
        }
        if (normalized.startsWith(QWEN_ASR_FLASH_REALTIME_MODEL)) {
            return normalized;
        }
        return model.strip();
    }

    private URI connectionUri(AsrProtocol protocol) {
        String url = properties().getWebsocketUrl().strip();
        if (protocol == AsrProtocol.QWEN_REALTIME) {
            url = qwenRealtimeUrl(url, resolvedModel());
        }
        return URI.create(url);
    }

    private String qwenRealtimeUrl(String websocketUrl, String model) {
        String url = websocketUrl.replace("/api-ws/v1/inference", "/api-ws/v1/realtime");
        if (url.matches(".*[?&]model=.*")) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "model=" + URLEncoder.encode(model, StandardCharsets.UTF_8);
    }

    private String startMessage(AsrProtocol protocol, UUID taskId) {
        return protocol == AsrProtocol.QWEN_REALTIME ? qwenSessionUpdateMessage(taskId) : runTaskMessage(taskId);
    }

    private String finishMessage(AsrProtocol protocol, UUID taskId) {
        return protocol == AsrProtocol.QWEN_REALTIME ? qwenSessionFinishMessage(taskId) : finishTaskMessage(taskId);
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
                                "sample_rate", properties().getSampleRate()),
                        "input", Map.of())));
    }

    private String finishTaskMessage(UUID taskId) {
        return toJson(Map.of(
                "header", Map.of(
                        "action", "finish-task",
                        "task_id", taskId.toString(),
                        "streaming", "duplex"),
                "payload", Map.of("input", Map.of())));
    }

    private String qwenSessionUpdateMessage(UUID eventId) {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("input_audio_format", properties().getFormat());
        session.put("sample_rate", properties().getSampleRate());
        session.put("input_audio_transcription", Map.of("language", "zh"));
        session.put("turn_detection", Map.of(
                "type", "server_vad",
                "threshold", 0.0,
                "silence_duration_ms", 400));
        return toJson(Map.of(
                "event_id", eventId(eventId),
                "type", "session.update",
                "session", session));
    }

    private String qwenInputAudioAppendMessage(byte[] audio) {
        return toJson(Map.of(
                "event_id", eventId(UuidV7Generator.generate()),
                "type", "input_audio_buffer.append",
                "audio", Base64.getEncoder().encodeToString(audio)));
    }

    private String qwenSessionFinishMessage(UUID eventId) {
        return toJson(Map.of(
                "event_id", eventId(eventId),
                "type", "session.finish"));
    }

    private String eventId(UUID id) {
        return "event_" + id.toString().replace("-", "");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private enum AsrProtocol {
        PARAFORMER,
        QWEN_REALTIME
    }

    /**
     * ASR 转录结果监听器
     */
    public interface AsrListener {

        /**
         * 收到转录结果
         *
         * @param transcript 转录结果
         */
        void onTranscript(AsrTranscript transcript);

        /**
         * 发生错误
         *
         * @param error 异常信息
         */
        void onError(Throwable error);
    }

    public record AsrTranscript(String text, boolean sentenceEnd, Integer beginTimeMs, Integer endTimeMs) {
    }

    public static class LiveSummaryAsrStream implements AutoCloseable {

        private final CompletableFuture<WebSocket> socketFuture;
        private final CompletableFuture<WebSocket> taskStartedFuture;
        private final CompletableFuture<WebSocket> taskFinishedFuture;
        private final String finishTaskMessage;
        private final Function<byte[], String> audioMessageFactory;
        private CompletableFuture<WebSocket> sendChain;

        private LiveSummaryAsrStream(CompletableFuture<WebSocket> socketFuture,
                                     CompletableFuture<WebSocket> taskStartedFuture,
                                     CompletableFuture<WebSocket> taskFinishedFuture,
                                     String finishTaskMessage,
                                     Function<byte[], String> audioMessageFactory) {
            this.socketFuture = socketFuture;
            this.taskStartedFuture = taskStartedFuture;
            this.taskFinishedFuture = taskFinishedFuture;
            this.sendChain = socketFuture.thenApply(webSocket -> webSocket);
            this.finishTaskMessage = finishTaskMessage;
            this.audioMessageFactory = audioMessageFactory;
        }

        public synchronized void sendAudio(byte[] audio) {
            if (audio == null || audio.length == 0) {
                return;
            }
            byte[] chunk = audio.clone();
            sendChain = sendChain.thenCompose(webSocket -> taskStartedFuture.thenCompose(ignored ->
                    audioMessageFactory == null
                            ? webSocket.sendBinary(ByteBuffer.wrap(chunk), true)
                            : webSocket.sendText(audioMessageFactory.apply(chunk), true)));
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
        private final AsrProtocol protocol;
        private final StringBuilder textBuffer = new StringBuilder();

        private AsrWebSocketListener(AsrListener listener,
                                     CompletableFuture<WebSocket> taskStartedFuture,
                                     CompletableFuture<WebSocket> taskFinishedFuture,
                                     AsrProtocol protocol) {
            this.listener = listener;
            this.taskStartedFuture = taskStartedFuture;
            this.taskFinishedFuture = taskFinishedFuture;
            this.protocol = protocol;
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
            if (protocol == AsrProtocol.QWEN_REALTIME) {
                handleQwenMessage(webSocket, message, event);
                return;
            }
            if (EVENT_TASK_STARTED.equals(event)) {
                taskStartedFuture.complete(webSocket);
                return;
            }
            if (EVENT_TASK_FINISHED.equals(event)) {
                taskFinishedFuture.complete(webSocket);
                return;
            }
            if (EVENT_TASK_FAILED.equals(event)) {
                String errorMessage = resultParser.extractErrorMessage(message);
                RuntimeException failure = new IllegalStateException(
                        StringUtils.hasText(errorMessage) ? errorMessage : "DashScope ASR task failed");
                taskStartedFuture.completeExceptionally(failure);
                taskFinishedFuture.completeExceptionally(failure);
                listener.onError(failure);
                webSocket.abort();
                return;
            }
            if (!EVENT_RESULT_GENERATED.equals(event)) {
                return;
            }
            resultParser.parse(message).forEach(listener::onTranscript);
        }

        private void handleQwenMessage(WebSocket webSocket, String message, String event) {
            if (EVENT_SESSION_UPDATED.equals(event)) {
                taskStartedFuture.complete(webSocket);
                return;
            }
            if (EVENT_SESSION_FINISHED.equals(event)) {
                taskFinishedFuture.complete(webSocket);
                return;
            }
            if (EVENT_ERROR.equals(event) || EVENT_QWEN_TRANSCRIPTION_FAILED.equals(event)) {
                String errorMessage = resultParser.extractErrorMessage(message);
                RuntimeException failure = new IllegalStateException(
                        StringUtils.hasText(errorMessage) ? errorMessage : "DashScope ASR task failed");
                taskStartedFuture.completeExceptionally(failure);
                taskFinishedFuture.completeExceptionally(failure);
                listener.onError(failure);
                webSocket.abort();
                return;
            }
            resultParser.parse(message).forEach(listener::onTranscript);
        }
    }
}
