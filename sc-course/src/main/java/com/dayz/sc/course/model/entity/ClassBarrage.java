package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 课堂弹幕消息实体
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Getter
@Setter
@TableName("edu_class_barrage")
public class ClassBarrage {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("session_id")
    private UUID sessionId;

    @TableField("sender_id")
    private UUID senderId;

    @TableField("content")
    private String content;

    @TableField("sent_at")
    private Instant sentAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
