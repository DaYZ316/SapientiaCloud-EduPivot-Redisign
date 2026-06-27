package com.dayz.sc.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 知识库文档实体
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Getter
@Setter
@TableName("ai_knowledge_doc")
public class KnowledgeDoc {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("storage_object_id")
    private UUID storageObjectId;

    @TableField("filename")
    private String filename;

    @TableField("status")
    private String status;

    @TableField("chunk_count")
    private Integer chunkCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;

    @TableField("deleted_at")
    private Instant deletedAt;
}
