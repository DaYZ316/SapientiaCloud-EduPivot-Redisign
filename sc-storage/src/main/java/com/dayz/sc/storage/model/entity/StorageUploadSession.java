package com.dayz.sc.storage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("storage_upload_session")
public class StorageUploadSession {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID objectId;

    private String method;

    private Instant expiresAt;

    private Long maxSizeBytes;

    private String allowedContentType;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    private Instant completedAt;
}
