package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record CourseAccessVO(
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("canManage") boolean canManage,
        @JsonProperty("canReadPublic") boolean canReadPublic,
        @JsonProperty("canReadPrivate") boolean canReadPrivate,
        @JsonProperty("isPrimaryTeacher") boolean isPrimaryTeacher
) {}
