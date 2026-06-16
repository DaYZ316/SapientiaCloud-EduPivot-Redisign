package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * CourseFile 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("edu_course_file")
public class CourseFile {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("course_id")
    private UUID courseId;

    @TableField("storage_object_id")
    private UUID storageObjectId;

    @TableField("visibility")
    private String visibility;

    @TableField("display_name")
    private String displayName;

    @TableField("created_by")
    private UUID createdBy;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
