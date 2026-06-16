package com.dayz.sc.common.events;

import com.dayz.sc.common.util.UuidV7Generator;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record ScEvent(
        UUID eventId,
        String eventType,
        Instant timestamp,
        String source
) {
    public static ScEvent of(String eventType, String source) {
        return new ScEvent(UuidV7Generator.generate(), eventType, Instant.now(), source);
    }
}
