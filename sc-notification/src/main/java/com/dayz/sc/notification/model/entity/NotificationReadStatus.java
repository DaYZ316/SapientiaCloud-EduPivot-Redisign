package com.dayz.sc.notification.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 通知已读状态实体。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Data
@TableName("ntf_read_status")
public class NotificationReadStatus {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID notificationId;
    private UUID userId;
    private Instant readAt;
}
