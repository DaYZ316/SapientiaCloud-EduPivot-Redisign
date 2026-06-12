package com.dayz.sc.course.model.dto;

import java.util.UUID;

public record ForumReplyPageRequest(
        Long page,
        Long size,
        UUID postId,
        UUID forumId,
        UUID courseId,
        Integer status
) {}
