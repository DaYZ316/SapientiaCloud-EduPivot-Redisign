package com.dayz.sc.course.model.dto;

import java.util.UUID;

public record ForumPostPageRequest(
        Long page,
        Long size,
        UUID forumId,
        UUID courseId,
        Integer status,
        String keyword
) {}
