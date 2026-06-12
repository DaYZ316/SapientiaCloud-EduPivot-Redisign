package com.dayz.sc.storage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 存储对象元数据实体
 *
 * @author DaYZ
 * @since 2026-06-10
 */
@Data
@TableName("storage_object")
public class StorageObject {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("bucket")
    private String bucket;

    private String objectKey;

    private String originalFilename;

    private String contentType;

    private Long sizeBytes;

    private String etag;

    private String sha256;

    private String usage;

    private String visibility;

    private UUID ownerUserId;

    private String scopeType;

    private UUID scopeId;

    private String status;

    /**
     * 软删除标记（0=正常, 1=已删除）
     */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    private Instant uploadedAt;

    private Instant deletedAt;
}
