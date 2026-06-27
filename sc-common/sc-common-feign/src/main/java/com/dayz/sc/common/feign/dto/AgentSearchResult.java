package com.dayz.sc.common.feign.dto;

import java.util.Map;
import java.util.UUID;

/**
 * AgentSearchResult.
 *
 * @author DaYZ
 */
public record AgentSearchResult(
        String sourceType,
        UUID sourceId,
        UUID courseId,
        String title,
        String snippet,
        Map<String, Object> metadata
) {
}
