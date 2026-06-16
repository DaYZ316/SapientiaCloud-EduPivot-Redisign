package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 视图对象。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public record CourseVO(
        @JsonProperty UUID id,
        @JsonProperty String title,
        @JsonProperty @Nullable String description,
        @JsonProperty UUID teacherId,
        @JsonProperty @Nullable String teacherName,
        @JsonProperty @Nullable String teacherAvatar,
        @JsonProperty int level,
        @JsonProperty @Nullable String coverUrl,
        @JsonProperty @Nullable UUID coverFileId,
        @JsonProperty @Nullable List<UUID> teacherIds,
        @JsonProperty @Nullable String semester,
        @JsonProperty @Nullable String location,
        @JsonProperty Integer courseType,
        @JsonProperty Integer isPublic,
        @JsonProperty int maxStudents,
        @JsonProperty int currentStudents,
        @JsonProperty int status,
        @JsonProperty Instant createdAt,
        @JsonProperty Instant updatedAt
) {}
