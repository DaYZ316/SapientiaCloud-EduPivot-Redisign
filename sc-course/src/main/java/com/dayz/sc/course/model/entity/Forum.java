package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@TableName("edu_forum")
public class Forum {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID courseId;

    private String forumName;

    private String description;

    private Integer forumType;

    private Integer allowAnonymous;

    private Long postCount;

    private Long replyCount;

    private Integer status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
