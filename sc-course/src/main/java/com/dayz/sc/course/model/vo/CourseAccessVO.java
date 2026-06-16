package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * 视图对象
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CourseAccessVO(
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("canManage") boolean canManage,
        @JsonProperty("canReadPublic") boolean canReadPublic,
        @JsonProperty("canReadPrivate") boolean canReadPrivate,
        @JsonProperty("isPrimaryTeacher") boolean primaryTeacher
) {
}
