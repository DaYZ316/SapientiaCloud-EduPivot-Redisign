package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@TableName("edu_question_option")
public class QuestionOption {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID questionId;

    private UUID courseId;

    private String optionContent;

    private String optionLabel;

    private Integer isCorrect;

    private BigDecimal score;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    private String explanation;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
