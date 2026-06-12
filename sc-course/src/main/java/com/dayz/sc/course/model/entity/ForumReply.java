package com.dayz.sc.course.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@TableName("edu_forum_reply")
public class ForumReply {

    @TableId(type = IdType.INPUT)
    private UUID id;

    private UUID postId;

    private UUID forumId;

    private UUID courseId;

    private UUID sysUserId;

    private String content;

    private UUID parentReplyId;

    private UUID replyToUserId;

    private Integer isAnonymous;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> attachmentUrls;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageUrls;

    private Long likeCount;

    private Long replyCount;

    private Integer isAccepted;

    private Integer floorNumber;

    private Integer status;

    private String ipAddress;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableLogic
    private Integer deleted;
}
