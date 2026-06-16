package com.dayz.sc.auth.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 教师信息扩展表
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Getter
@Setter
@TableName("edu_teacher")
public class Teacher {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private UUID userId;

    @TableField("employee_no")
    private String employeeNo;

    @TableField("department")
    private String department;

    @TableField("title")
    private String title;

    @TableField("school")
    private String school;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
