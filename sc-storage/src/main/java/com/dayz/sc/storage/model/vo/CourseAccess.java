package com.dayz.sc.storage.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * CourseAccess 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CourseAccess(
        UUID courseId,
        boolean canManage,
        boolean canReadPublic,
        boolean canReadPrivate,
        @JsonProperty("isPrimaryTeacher") boolean primaryTeacher
) {
}
