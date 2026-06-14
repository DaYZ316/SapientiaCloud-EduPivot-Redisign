package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.dayz.sc.course.config.PostgresJsonbStringListTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@TableName(value = "edu_forum_post", autoResultMap = true)
public class ForumPost {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("course_id")
    private UUID courseId;

    @TableField("sys_user_id")
    private UUID sysUserId;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("post_type")
    private Integer postType;

    @TableField(typeHandler = PostgresJsonbStringListTypeHandler.class)
    private List<String> attachmentUrls;

    @TableField(typeHandler = PostgresJsonbStringListTypeHandler.class)
    private List<String> imageUrls;

    @TableField(typeHandler = PostgresJsonbStringListTypeHandler.class)
    private List<String> tags;

    @TableField("view_count")
    private Long viewCount;

    @TableField("like_count")
    private Long likeCount;

    @TableField("reply_count")
    private Long replyCount;

    @TableField("share_count")
    private Long shareCount;

    @TableField("is_top")
    private Integer isTop;

    @TableField("is_essence")
    private Integer isEssence;

    @TableField("is_locked")
    private Integer isLocked;

    @TableField("last_reply_id")
    private UUID lastReplyId;

    @TableField("last_reply_time")
    private Instant lastReplyTime;

    @TableField("last_reply_user_id")
    private UUID lastReplyUserId;

    @TableField("status")
    private Integer status;

    @TableField("chapter_id")
    private UUID chapterId;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
