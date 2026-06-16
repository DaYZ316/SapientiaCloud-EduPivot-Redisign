package com.dayz.sc.notification.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 通知目标用户实体
 *
 * @author DaYZ
 * @since 2026-06-11
 */
@Getter
@Setter
@TableName("ntf_notification_target")
public class NotificationTarget {
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("notification_id")
    private UUID notificationId;
    @TableField("user_id")
    private UUID userId;
    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
