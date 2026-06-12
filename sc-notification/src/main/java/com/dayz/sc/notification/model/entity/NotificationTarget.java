package com.dayz.sc.notification.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.UUID;

/**
 * 通知目标用户实体。
 *
 * @author DaYZ
 * @since 2026-06-11
 */
@Data
@TableName("ntf_notification_target")
public class NotificationTarget {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID notificationId;
    private UUID userId;
    @TableLogic
    private Integer deleted;
}
