package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@TableName("edu_question")
public class Question {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID questionBankId;

    private UUID courseId;

    private UUID sysUserId;

    private String questionTitle;

    private String questionContent;

    private Integer questionType;

    private Integer difficulty;

    private BigDecimal score;

    private Integer estimatedTime;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    private Integer allowPartialCredit;

    private Long viewCount;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
