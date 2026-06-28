package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeAsrClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void runTaskMessageShouldIncludeRequiredEmptyInput() throws Exception {
        DashScopeAsrClient client = client();

        String message = (String) ReflectionTestUtils.invokeMethod(
                client,
                "runTaskMessage",
                UUID.fromString("019f0000-0000-7000-8000-000000000001"));
        JsonNode payload = objectMapper.readTree(message).path("payload");

        assertThat(payload.path("task_group").asText()).isEqualTo("audio");
        assertThat(payload.path("input").isObject()).isTrue();
        assertThat(payload.path("input").isEmpty()).isTrue();
    }

    @Test
    void finishTaskMessageShouldIncludeRequiredEmptyInput() throws Exception {
        DashScopeAsrClient client = client();

        String message = (String) ReflectionTestUtils.invokeMethod(
                client,
                "finishTaskMessage",
                UUID.fromString("019f0000-0000-7000-8000-000000000001"));
        JsonNode payload = objectMapper.readTree(message).path("payload");

        assertThat(payload.path("input").isObject()).isTrue();
        assertThat(payload.path("input").isEmpty()).isTrue();
    }

    @Test
    void qwenFlashModelShouldResolveToRealtimeModel() {
        AiProperties properties = new AiProperties();
        properties.getLiveSummary().getAsr().setModel("Qwen3-ASR-Flash");
        DashScopeAsrClient client = client(properties);

        String model = (String) ReflectionTestUtils.invokeMethod(client, "resolvedModel");

        assertThat(model).isEqualTo("qwen3-asr-flash-realtime");
    }

    @Test
    void qwenSessionUpdateMessageShouldUseRealtimeAudioSettings() throws Exception {
        AiProperties properties = new AiProperties();
        properties.getLiveSummary().getAsr().setModel("qwen3-asr-flash-realtime");
        properties.getLiveSummary().getAsr().setFormat("pcm");
        properties.getLiveSummary().getAsr().setSampleRate(16000);
        DashScopeAsrClient client = client(properties);

        String message = (String) ReflectionTestUtils.invokeMethod(
                client,
                "qwenSessionUpdateMessage",
                UUID.fromString("019f0000-0000-7000-8000-000000000001"));
        JsonNode root = objectMapper.readTree(message);

        assertThat(root.path("type").asText()).isEqualTo("session.update");
        assertThat(root.path("session").path("input_audio_format").asText()).isEqualTo("pcm");
        assertThat(root.path("session").path("sample_rate").asInt()).isEqualTo(16000);
        assertThat(root.path("session").path("input_audio_transcription").path("language").asText()).isEqualTo("zh");
        assertThat(root.path("session").path("turn_detection").path("type").asText()).isEqualTo("server_vad");
    }

    @Test
    void qwenAudioAppendMessageShouldBase64EncodePcmChunk() throws Exception {
        DashScopeAsrClient client = client();

        String message = (String) ReflectionTestUtils.invokeMethod(
                client,
                "qwenInputAudioAppendMessage",
                new byte[]{1, 2, 3});
        JsonNode root = objectMapper.readTree(message);

        assertThat(root.path("type").asText()).isEqualTo("input_audio_buffer.append");
        assertThat(root.path("audio").asText()).isEqualTo("AQID");
    }

    private DashScopeAsrClient client() {
        AiProperties properties = new AiProperties();
        return client(properties);
    }

    private DashScopeAsrClient client(AiProperties properties) {
        properties.getLiveSummary().getAsr().setApiKey("test-key");
        properties.getLiveSummary().getAsr().setWebsocketUrl("wss://example.test");
        return new DashScopeAsrClient(properties, objectMapper, new DashScopeAsrResultParser(objectMapper));
    }
}
