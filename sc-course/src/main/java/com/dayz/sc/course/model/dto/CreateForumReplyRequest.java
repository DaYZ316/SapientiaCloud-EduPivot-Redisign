package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateForumReplyRequest(
        @NotNull UUID postId,
        @NotBlank String content,
        UUID parentReplyId,
        UUID replyToUserId,
        List<String> attachmentUrls,
        List<String> imageUrls
) {}
