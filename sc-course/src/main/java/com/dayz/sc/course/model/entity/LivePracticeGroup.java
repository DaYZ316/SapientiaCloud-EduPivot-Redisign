package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 随堂练习分组实体
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Getter
@Setter
@TableName("edu_live_practice_group")
public class LivePracticeGroup {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID courseId;

    private UUID classSessionId;

    private UUID teacherId;

    private String title;

    private Instant availableStartAt;

    private Instant availableEndAt;

    private Integer allowLateSubmission;

    private Integer aiGradingEnabled;

    private String aiGradingRequirement;

    private Integer publishOrder;

    private Instant publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
