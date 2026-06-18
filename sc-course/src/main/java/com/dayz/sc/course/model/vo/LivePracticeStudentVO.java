package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * 随堂练习学生视图对象
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record LivePracticeStudentVO(
        @JsonProperty UUID id,
        @JsonProperty String displayName,
        @JsonProperty String avatarUrl
) {
}
