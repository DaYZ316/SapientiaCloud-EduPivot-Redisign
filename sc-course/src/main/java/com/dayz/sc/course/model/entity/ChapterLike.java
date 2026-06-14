package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@TableName("edu_chapter_like")
public class ChapterLike {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID chapterId;

    private UUID courseId;

    private UUID userId;

    private Instant createdAt;
}
