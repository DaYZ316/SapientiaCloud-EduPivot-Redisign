package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.Map;

public record GenerationStageEvent(
        String requestId,
        String mode,
        String stage,
        String status,
        String title,
        String summary,
        Map<String, Object> payload,
        Instant timestamp
) {
    public static GenerationStageEvent of(String requestId,
                                          String mode,
                                          String stage,
                                          String status,
                                          String title,
                                          String summary,
                                          Map<String, Object> payload) {
        return new GenerationStageEvent(
                requestId,
                mode,
                stage,
                status,
                title,
                summary,
                payload == null ? Map.of() : payload,
                Instant.now());
    }
}
