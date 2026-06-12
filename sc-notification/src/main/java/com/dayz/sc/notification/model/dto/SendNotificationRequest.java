package com.dayz.sc.notification.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 发送通知请求。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public record SendNotificationRequest(
        @NotNull @Min(1) Integer type,
        @NotBlank @Size(max = 128) String title,
        @NotBlank String content,
        @NotNull @Min(0) Integer targetType,
        List<UUID> userIds
) {
}
