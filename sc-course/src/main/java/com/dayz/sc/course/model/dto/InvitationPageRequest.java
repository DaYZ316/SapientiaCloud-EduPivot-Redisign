package com.dayz.sc.course.model.dto;

import org.jspecify.annotations.Nullable;

public record InvitationPageRequest(
        @Nullable Long page,
        @Nullable Long size,
        @Nullable Integer status
) {
}
