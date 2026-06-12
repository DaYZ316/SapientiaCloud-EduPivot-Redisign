package com.dayz.sc.notification.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 通知实体。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Data
@TableName("ntf_notification")
public class Notification {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private Integer type;
    private String title;
    private String content;
    private UUID senderId;
    private Integer targetType;
    private Instant createdAt;
    private Instant updatedAt;
    @TableLogic
    private Integer deleted;
}
