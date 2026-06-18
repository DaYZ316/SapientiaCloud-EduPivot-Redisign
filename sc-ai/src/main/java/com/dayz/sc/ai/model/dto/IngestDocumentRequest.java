package com.dayz.sc.ai.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 知识库文档入库请求。文件须已通过 sc-storage 上传，这里传入其文件对象 ID。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
public record IngestDocumentRequest(
        @NotNull UUID storageObjectId
) {
}
