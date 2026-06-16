package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Question 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("edu_question")
public class Question {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("question_bank_id")
    private UUID questionBankId;

    @TableField("course_id")
    private UUID courseId;

    @TableField("sys_user_id")
    private UUID sysUserId;

    @TableField("question_title")
    private String questionTitle;

    @TableField("question_content")
    private String questionContent;

    @TableField("question_type")
    private Integer questionType;

    @TableField("difficulty")
    private Integer difficulty;

    @TableField("score")
    private BigDecimal score;

    @TableField("estimated_time")
    private Integer estimatedTime;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    @TableField("allow_partial_credit")
    private Integer allowPartialCredit;

    @TableField("view_count")
    private Long viewCount;

    @TableField("status")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
