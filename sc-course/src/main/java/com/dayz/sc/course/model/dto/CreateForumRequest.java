package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateForumRequest(
        @NotNull UUID courseId,
        @NotBlank @Size(max = 200) String forumName,
        @Size(max = 2000) String description,
        int forumType,
        List<String> tags
) {}
