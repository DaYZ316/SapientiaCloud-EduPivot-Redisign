package com.dayz.sc.course.model.dto;

import java.util.UUID;

public record QuestionBankPageRequest(
        Long page,
        Long size,
        UUID courseId,
        Integer bankType,
        String keyword
) {}
