package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chapter 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("edu_chapter")
public class Chapter {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID courseId;

    private UUID teacherId;

    private String chapterName;

    private UUID parentChapterId;

    private String description;

    private String content;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;

    private Integer sortOrder;

    private Integer status;

    private Long viewCount;

    private Long likeCount;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
