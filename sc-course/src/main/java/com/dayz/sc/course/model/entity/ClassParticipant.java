package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Class session participant.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Getter
@Setter
@TableName("edu_class_participant")
public class ClassParticipant {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("session_id")
    private UUID sessionId;

    @TableField("user_id")
    private UUID userId;

    @TableField("role")
    private Integer role;

    @TableField("x")
    private BigDecimal x;

    @TableField("y")
    private BigDecimal y;

    @TableField("z")
    private BigDecimal z;

    @TableField("joined_at")
    private Instant joinedAt;
}
