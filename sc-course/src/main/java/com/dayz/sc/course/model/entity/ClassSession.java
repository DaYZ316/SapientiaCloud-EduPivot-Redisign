package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Class session record.
 */
@Getter
@Setter
@TableName("edu_class_session")
public class ClassSession {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("course_id")
    private UUID courseId;

    @TableField("teacher_id")
    private UUID teacherId;

    @TableField("title")
    private String title;

    @TableField("description")
    private String description;

    @TableField("scheduled_start_at")
    private Instant scheduledStartAt;

    @TableField("scheduled_end_at")
    private Instant scheduledEndAt;

    @TableField("published_at")
    private Instant publishedAt;

    @TableField("room_size")
    private Integer roomSize;

    @TableField("live_room_name")
    private String liveRoomName;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
