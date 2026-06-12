package com.dayz.sc.course.model.dto;

import java.util.UUID;

public record ForumPageRequest(
        Long page,
        Long size,
        UUID courseId,
        Integer forumType,
        Integer status
) {}
