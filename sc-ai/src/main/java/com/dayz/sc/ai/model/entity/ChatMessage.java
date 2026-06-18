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
 * AI 助手对话消息实体
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Getter
@Setter
@TableName("ai_message")
public class ChatMessage {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("conversation_id")
    private UUID conversationId;

    @TableField("role")
    private String role;

    @TableField("content")
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
