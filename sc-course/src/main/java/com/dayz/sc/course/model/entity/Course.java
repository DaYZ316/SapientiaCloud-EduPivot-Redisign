package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("edu_course")
public class Course {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private String title;

    private String description;

    private UUID teacherId;

    private Integer level;

    private String coverUrl;

    private UUID coverFileId;

    private String semester;

    private String location;

    private Integer courseType;

    private Integer maxStudents;

    private Integer isPublic;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
