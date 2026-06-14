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
@TableName(value = "edu_forum_reply", autoResultMap = true)
public class ForumReply {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("post_id")
    private UUID postId;

    @TableField("course_id")
    private UUID courseId;

    @TableField("sys_user_id")
    private UUID sysUserId;

    @TableField("content")
    private String content;

    @TableField("parent_reply_id")
    private UUID parentReplyId;

    @TableField("reply_to_user_id")
    private UUID replyToUserId;

    @TableField(typeHandler = PostgresJsonbStringListTypeHandler.class)
    private List<String> attachmentUrls;

    @TableField(typeHandler = PostgresJsonbStringListTypeHandler.class)
    private List<String> imageUrls;

    @TableField("like_count")
    private Long likeCount;

    @TableField("reply_count")
    private Long replyCount;

    @TableField("is_accepted")
    private Integer isAccepted;

    @TableField("floor_number")
    private Integer floorNumber;

    @TableField("status")
    private Integer status;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("user_agent")
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    @TableField("deleted")
    @TableLogic
    private Integer deleted;
}
