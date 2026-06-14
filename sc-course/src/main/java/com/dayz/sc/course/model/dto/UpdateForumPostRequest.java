package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateForumPostRequest(
        @Size(max = 500) String title,
        String content,
        Integer postType,
        List<String> attachmentUrls,
        List<String> imageUrls,
        List<String> tags,
        Integer status
) {}
