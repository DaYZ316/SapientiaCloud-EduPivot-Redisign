package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@TableName("edu_forum")
public class Forum {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("course_id")
    private UUID courseId;

    @TableField("forum_name")
    private String forumName;

    @TableField("description")
    private String description;

    @TableField("forum_type")
    private Integer forumType;

    @TableField("post_count")
    private Long postCount;

    @TableField("reply_count")
    private Long replyCount;

    @TableField("status")
    private Integer status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
