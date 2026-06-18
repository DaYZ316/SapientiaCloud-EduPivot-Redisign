package com.dayz.sc.ai.model.vo;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识库文档视图对象。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record KnowledgeDocVO(
        UUID id,
        String filename,
        String status,
        int chunkCount,
        Instant createdAt
) {
}
