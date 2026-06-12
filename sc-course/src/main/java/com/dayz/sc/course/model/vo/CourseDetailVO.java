package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CourseDetailVO(
        @JsonProperty UUID id,
        @JsonProperty String title,
        @JsonProperty @Nullable String description,
        @JsonProperty UUID teacherId,
        @JsonProperty @Nullable String teacherName,
        @JsonProperty @Nullable String teacherAvatar,
        @JsonProperty int level,
        @JsonProperty @Nullable String coverUrl,
        @JsonProperty @Nullable List<UUID> teacherIds,
        @JsonProperty int maxStudents,
        @JsonProperty int currentStudents,
        @JsonProperty int status,
        @JsonProperty boolean enrolled,
        @JsonProperty Instant createdAt,
        @JsonProperty Instant updatedAt
) {}
