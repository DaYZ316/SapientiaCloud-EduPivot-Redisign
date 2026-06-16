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
 * PracticeAnswer 相关定义
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Getter
@Setter
@TableName("edu_practice_answer")
public class PracticeAnswer {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID sessionId;

    private UUID questionId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<UUID> selectedOptionIds;

    private String textAnswer;

    private Integer isCorrect;

    private BigDecimal earnedScore;

    @TableField(fill = FieldFill.INSERT)
    private Instant answeredAt;
}
