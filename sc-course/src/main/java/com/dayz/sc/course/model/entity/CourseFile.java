package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("edu_course_file")
public class CourseFile {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID courseId;

    private UUID storageObjectId;

    private String visibility;

    private String displayName;

    private UUID createdBy;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
