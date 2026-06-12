package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
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
