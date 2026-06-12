package com.dayz.sc.notification.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 通知实体。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("ntf_notification")
public class Notification {
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("type")
    private Integer type;
    @TableField("title")
    private String title;
    @TableField("content")
    private String content;
    @TableField("sender_id")
    private UUID senderId;
    @TableField("target_type")
    private Integer targetType;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
