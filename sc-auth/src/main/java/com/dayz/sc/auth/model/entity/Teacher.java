package com.dayz.sc.auth.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 教师信息扩展表。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Data
@TableName("edu_teacher")
public class Teacher {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID userId;

    private String employeeNo;

    private String department;

    private String title;

    private String school;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
