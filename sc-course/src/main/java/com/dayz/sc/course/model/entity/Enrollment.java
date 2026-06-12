package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("edu_enrollment")
public class Enrollment {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID courseId;

    private UUID studentId;

    private Integer status;

    private Instant enrolledAt;

    private Instant completedAt;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
