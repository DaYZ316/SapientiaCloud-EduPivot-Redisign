package com.dayz.sc.course.model.dto;

import java.util.UUID;

public record QuestionPageRequest(
        Long page,
        Long size,
        UUID questionBankId,
        UUID courseId,
        Integer questionType,
        Integer difficulty,
        Integer status,
        String keyword
) {}
