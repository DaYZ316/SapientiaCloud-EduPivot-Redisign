package com.dayz.sc.ai.model.enums;

import java.util.Locale;

/**
 * AI Agent 工作模式
 *
 * @author DaYZ
 * @since 2026-06-27
 */
public enum AiAgentMode {

    /**
     * 对话模式
     */
    CHAT,

    /**
     * 出题模式
     */
    QUESTION,

    /**
     * 组卷模式
     */
    PAPER;

    private static final String PAPER_KEYWORD_CREATE = "出卷";
    private static final String PAPER_KEYWORD_EXAM = "试卷";
    private static final String PAPER_KEYWORD_EN = "paper";
    private static final String QUESTION_KEYWORD_CREATE = "出题";
    private static final String QUESTION_KEYWORD_TITLE = "题目";
    private static final String QUESTION_KEYWORD_EN = "question";

    public static AiAgentMode resolve(String value, String message) {
        if (value != null && !value.isBlank()) {
            try {
                return AiAgentMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CHAT;
            }
        }
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains(PAPER_KEYWORD_CREATE)
                || normalized.contains(PAPER_KEYWORD_EXAM)
                || normalized.contains(PAPER_KEYWORD_EN)) {
            return PAPER;
        }
        if (normalized.contains(QUESTION_KEYWORD_CREATE)
                || normalized.contains(QUESTION_KEYWORD_TITLE)
                || normalized.contains(QUESTION_KEYWORD_EN)) {
            return QUESTION;
        }
        return CHAT;
    }
}
