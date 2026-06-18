package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayz.sc.course.config.PostgresJsonbUuidListTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 随堂练习提交记录实体
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Getter
@Setter
@TableName(value = "edu_live_practice_submission", autoResultMap = true)
public class LivePracticeSubmission {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID groupId;

    private UUID questionSnapshotId;

    private UUID courseId;

    private UUID classSessionId;

    private UUID studentId;

    @TableField(typeHandler = PostgresJsonbUuidListTypeHandler.class)
    private List<UUID> selectedOptionIds;

    private String textAnswer;

    private Integer submitStatus;

    private Integer isCorrect;

    private BigDecimal earnedScore;

    private String aiGradingStatus;

    private String aiGradingFeedback;

    private String aiGradingError;

    private Instant aiGradedAt;

    private Instant submittedAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}
