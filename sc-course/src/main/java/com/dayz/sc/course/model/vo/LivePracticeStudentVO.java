package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record LivePracticeStudentVO(
        @JsonProperty UUID id,
        @JsonProperty String displayName,
        @JsonProperty String avatarUrl
) {
}
