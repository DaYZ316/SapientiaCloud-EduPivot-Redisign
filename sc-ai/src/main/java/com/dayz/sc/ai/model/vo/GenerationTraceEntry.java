package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.Map;

/**
 * GenerationTraceEntry.
 *
 * @author DaYZ
 */
public record GenerationTraceEntry(
        String entryId,
        String stage,
        String source,
        String detailType,
        String title,
        String summary,
        Map<String, Object> payload,
        Instant timestamp
) {
}
