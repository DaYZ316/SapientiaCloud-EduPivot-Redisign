package com.dayz.sc.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class DashScopeAsrResultParser {

    private final ObjectMapper objectMapper;

    public DashScopeAsrResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DashScopeAsrClient.AsrTranscript> parse(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String event = root.path("header").path("event").asText("");
            if (!"result-generated".equals(event)) {
                return List.of();
            }
            JsonNode output = root.path("payload").path("output");
            List<DashScopeAsrClient.AsrTranscript> transcripts = new ArrayList<>();
            appendSentence(transcripts, output.path("sentence"));
            JsonNode sentences = output.path("sentences");
            if (sentences.isArray()) {
                sentences.forEach(sentence -> appendSentence(transcripts, sentence));
            }
            return transcripts;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    public String extractEvent(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            return root.path("header").path("event").asText("");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    public String extractErrorMessage(String rawMessage) {
        try {
            JsonNode root = objectMapper.readTree(rawMessage);
            String message = firstText(root.path("header"), "error_message", "message");
            if (StringUtils.hasText(message)) {
                return message;
            }
            message = firstText(root.path("payload"), "error_message", "message");
            if (StringUtils.hasText(message)) {
                return message;
            }
            return firstText(root.path("payload").path("output"), "error_message", "message");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private void appendSentence(List<DashScopeAsrClient.AsrTranscript> transcripts, JsonNode sentence) {
        if (!sentence.isObject()) {
            return;
        }
        String text = firstText(sentence, "text", "sentence_text", "sentence");
        if (!StringUtils.hasText(text)) {
            return;
        }
        transcripts.add(new DashScopeAsrClient.AsrTranscript(
                text.strip(),
                firstBoolean(sentence, "sentence_end", "sentenceEnd", "is_final", "isFinal"),
                firstInt(sentence, "begin_time", "beginTime", "start_time", "startTime"),
                firstInt(sentence, "end_time", "endTime", "stop_time", "stopTime")));
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean firstBoolean(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
        }
        return false;
    }

    private Integer firstInt(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isNumber()) {
                return value.asInt();
            }
        }
        return null;
    }
}
