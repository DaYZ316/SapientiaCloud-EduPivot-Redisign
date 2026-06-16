package com.dayz.sc.notification.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 通知已读状态实体
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("ntf_read_status")
public class NotificationReadStatus {
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("notification_id")
    private UUID notificationId;
    @TableField("user_id")
    private UUID userId;
    @TableField("read_at")
    private Instant readAt;
}
