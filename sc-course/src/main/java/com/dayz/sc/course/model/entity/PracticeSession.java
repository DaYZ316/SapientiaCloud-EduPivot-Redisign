package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * PracticeSession 相关定义
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Getter
@Setter
@TableName("edu_practice_session")
public class PracticeSession {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID questionBankId;

    private UUID courseId;

    private UUID sysUserId;

    private Integer sessionType;

    private Integer totalQuestions;

    private Integer answeredCount;

    private Integer correctCount;

    private BigDecimal totalScore;

    private BigDecimal earnedScore;

    @TableField(fill = FieldFill.INSERT)
    private Instant startedAt;

    private Instant completedAt;

    private Integer status;
}
