package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateQuestionBankRequest(
        @Size(max = 200) String bankName,
        @Size(max = 2000) String description,
        Integer bankType,
        List<String> tags,
        Integer difficulty
) {}
