package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * QuestionAnswer 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("edu_question_answer")
public class QuestionAnswer {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID questionId;

    private UUID courseId;

    private String answerContent;

    private String explanation;

    private BigDecimal score;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
