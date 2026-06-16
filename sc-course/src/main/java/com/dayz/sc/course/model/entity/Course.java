package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Course 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@Setter
@TableName("edu_course")
public class Course {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("teacher_id")
    private UUID teacherId;

    @TableField("level")
    private Integer level;

    @TableField("cover_url")
    private String coverUrl;

    @TableField("cover_file_id")
    private UUID coverFileId;

    @TableField("semester")
    private String semester;

    @TableField("location")
    private String location;

    @TableField("course_type")
    private Integer courseType;

    @TableField("max_students")
    private Integer maxStudents;

    @TableField("is_public")
    private Integer isPublic;

    @TableField("status")
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
