package com.dayz.sc.ai.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 助手会话实体。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Getter
@Setter
@TableName("ai_conversation")
public class Conversation {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("title")
    private String title;

    @TableField("pinned")
    private Integer pinned;

    @TableField("favorited")
    private Integer favorited;

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
