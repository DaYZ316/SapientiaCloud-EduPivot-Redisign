package com.dayz.sc.common.feign.dto;

import java.util.Map;

public record AgentSearchItem(
        String sourceType,
        String sourceLabel,
        String sourceId,
        String courseId,
        String title,
        String contextLabel,
        String snippet,
        String relationLabel,
        Map<String, Object> metadata,
        Map<String, Object> indexInfo
) {
    public AgentSearchItem(String sourceType,
                           String sourceLabel,
                           String title,
                           String contextLabel,
                           String snippet,
                           String relationLabel,
                           Map<String, Object> metadata) {
        this(sourceType, sourceLabel, null, null, title, contextLabel, snippet, relationLabel, metadata, Map.of());
    }
}
