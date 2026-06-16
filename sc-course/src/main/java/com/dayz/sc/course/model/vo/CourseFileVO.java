package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * 视图对象。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CourseFileVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("fileId") UUID fileId,
        @JsonProperty("visibility") String visibility,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("url") String url,
        @JsonProperty("createdBy") UUID createdBy,
        @JsonProperty("sortOrder") Integer sortOrder,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {}
