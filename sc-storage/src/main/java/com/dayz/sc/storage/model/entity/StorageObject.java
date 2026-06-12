package com.dayz.sc.storage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 存储对象元数据实体
 *
 * @author DaYZ
 * @since 2026-06-10
 */
@Getter
@Setter
@TableName("storage_object")
public class StorageObject {

    /** 软删除标记：已删除 */
    public static final int DELETED = 1;

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("bucket")
    private String bucket;

    @TableField("object_key")
    private String objectKey;

    @TableField("original_filename")
    private String originalFilename;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("etag")
    private String etag;

    @TableField("sha256")
    private String sha256;

    @TableField("usage")
    private String usage;

    @TableField("visibility")
    private String visibility;

    @TableField("owner_user_id")
    private UUID ownerUserId;

    @TableField("scope_type")
    private String scopeType;

    @TableField("scope_id")
    private UUID scopeId;

    @TableField("status")
    private String status;

    /**
     * 软删除标记（0=正常, 1=已删除）
     */
    @TableField("deleted")
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField("uploaded_at")
    private Instant uploadedAt;

    @TableField("deleted_at")
    private Instant deletedAt;
}
