package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@TableName("edu_forum_post")
public class ForumPost {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID forumId;

    private UUID courseId;

    private UUID sysUserId;

    private String title;

    private String content;

    private Integer postType;

    private Integer isAnonymous;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Long viewCount;

    private Long likeCount;

    private Long replyCount;

    private Long shareCount;

    private Integer isTop;

    private Integer isEssence;

    private Integer isLocked;

    private UUID lastReplyId;

    private Instant lastReplyTime;

    private UUID lastReplyUserId;

    private Integer status;

    private UUID chapterId;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
