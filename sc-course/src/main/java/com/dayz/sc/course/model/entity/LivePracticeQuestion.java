package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayz.sc.course.config.PostgresJsonbLivePracticeAnswerSnapshotListTypeHandler;
import com.dayz.sc.course.config.PostgresJsonbLivePracticeOptionSnapshotListTypeHandler;
import com.dayz.sc.course.config.PostgresJsonbStringListTypeHandler;
import com.dayz.sc.course.model.value.LivePracticeAnswerSnapshot;
import com.dayz.sc.course.model.value.LivePracticeOptionSnapshot;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 随堂练习题目快照实体
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Getter
@Setter
@TableName(value = "edu_live_practice_question", autoResultMap = true)
public class LivePracticeQuestion {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID groupId;

    private UUID courseId;

    private UUID classSessionId;

    private UUID sourceQuestionId;

    private Integer questionOrder;

    private String questionTitle;

    private String questionContent;

    private Integer questionType;

    private Integer difficulty;

    private BigDecimal score;

    private Integer estimatedTime;

    @TableField(typeHandler = PostgresJsonbStringListTypeHandler.class)
    private List<String> tags;

    @TableField(typeHandler = PostgresJsonbStringListTypeHandler.class)
    private List<String> imageUrls;

    private Integer allowPartialCredit;

    @TableField(typeHandler = PostgresJsonbLivePracticeOptionSnapshotListTypeHandler.class)
    private List<LivePracticeOptionSnapshot> optionsSnapshot;

    @TableField(typeHandler = PostgresJsonbLivePracticeAnswerSnapshotListTypeHandler.class)
    private List<LivePracticeAnswerSnapshot> answersSnapshot;

    private Integer aiGradingEnabled;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
