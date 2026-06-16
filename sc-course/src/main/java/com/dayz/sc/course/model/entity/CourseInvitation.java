package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * CourseInvitation 相关定义
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("edu_course_invitation")
public class CourseInvitation {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("course_id")
    private UUID courseId;

    @TableField("inviter_id")
    private UUID inviterId;

    @TableField("invitee_id")
    private UUID inviteeId;

    private Integer status;

    private String message;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
