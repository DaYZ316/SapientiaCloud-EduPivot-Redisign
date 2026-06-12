package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record CourseInvitationVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("courseId") UUID courseId,
        @JsonProperty("courseTitle") @Nullable String courseTitle,
        @JsonProperty("courseCoverUrl") @Nullable String courseCoverUrl,
        @JsonProperty("inviterId") UUID inviterId,
        @JsonProperty("inviterName") @Nullable String inviterName,
        @JsonProperty("inviterAvatar") @Nullable String inviterAvatar,
        @JsonProperty("inviteeId") UUID inviteeId,
        @JsonProperty("inviteeName") @Nullable String inviteeName,
        @JsonProperty("status") int status,
        @JsonProperty("message") @Nullable String message,
        @JsonProperty("createdAt") Instant createdAt
) {
}
