package com.dayz.sc.storage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * StorageUploadSession 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("storage_upload_session")
public class StorageUploadSession {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("object_id")
    private UUID objectId;

    @TableField("method")
    private String method;

    @TableField("expires_at")
    private Instant expiresAt;

    @TableField("max_size_bytes")
    private Long maxSizeBytes;

    @TableField("allowed_content_type")
    private String allowedContentType;

    @TableField("status")
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField("completed_at")
    private Instant completedAt;
}
