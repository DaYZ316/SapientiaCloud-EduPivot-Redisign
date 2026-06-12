package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateForumRequest(
        @Size(max = 200) String forumName,
        @Size(max = 2000) String description,
        Integer forumType,
        Integer allowAnonymous,
        List<String> tags,
        Integer status
) {}
