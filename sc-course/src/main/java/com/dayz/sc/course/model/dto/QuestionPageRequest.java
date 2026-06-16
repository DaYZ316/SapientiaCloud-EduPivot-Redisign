package com.dayz.sc.course.model.dto;

import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record QuestionPageRequest(
        Long page,
        Long size,
        UUID questionBankId,
        UUID courseId,
        Integer questionType,
        Integer difficulty,
        Integer status,
        String keyword
) {
}
