package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@TableName("edu_course_teacher")
public class CourseTeacher {

    private UUID courseId;

    private UUID teacherId;

    @TableField("created_at")
    private Instant createdAt;
}
