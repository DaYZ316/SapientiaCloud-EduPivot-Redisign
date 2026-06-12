package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateForumPostRequest(
        @NotNull UUID forumId,
        @NotBlank @Size(max = 500) String title,
        @NotBlank String content,
        int postType,
        int isAnonymous,
        List<String> attachmentUrls,
        List<String> imageUrls,
        List<String> tags,
        UUID chapterId
) {}
