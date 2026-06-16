package com.dayz.sc.course.model.entity;

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
 * Class session barrage message.
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
