package com.dayz.sc.course.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 请求 DTO
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public record InviteAssistantRequest(
        @NotNull UUID courseId,
        @NotNull UUID inviteeId,
        @Nullable @Size(max = 500) String message
) {
}
