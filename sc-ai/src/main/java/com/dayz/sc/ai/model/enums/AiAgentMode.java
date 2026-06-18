package com.dayz.sc.ai.model.enums;

import java.util.Locale;

public enum AiAgentMode {
    CHAT,
    QUESTION,
    PAPER;

    public static AiAgentMode resolve(String value, String message) {
        if (value != null && !value.isBlank()) {
            try {
                return AiAgentMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CHAT;
            }
        }
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("出卷") || normalized.contains("试卷") || normalized.contains("paper")) {
            return PAPER;
        }
        if (normalized.contains("出题") || normalized.contains("题目") || normalized.contains("question")) {
            return QUESTION;
        }
        return CHAT;
    }
}
