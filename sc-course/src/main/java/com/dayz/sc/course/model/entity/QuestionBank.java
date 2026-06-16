package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * QuestionBank 相关定义。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("edu_question_bank")
public class QuestionBank {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID courseId;

    private UUID sysUserId;

    private String bankName;

    private String description;

    private Integer bankType;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Integer difficulty;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
