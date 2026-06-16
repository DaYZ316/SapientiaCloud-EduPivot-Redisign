package com.dayz.sc.auth.model.dto;

import com.dayz.sc.auth.model.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 用户管理页面提交的账号资料更新参数
 *
 * @author DaYZ
 * @since 2026-05-08
 */
public record UpdateUserRequest(
        @Email
        @Size(max = 320)
        String email,
        Boolean emailVerified,
        @Size(max = 128)
        String displayName,
        @Size(max = 2048)
        String avatarUrl,
        UUID avatarFileId,
        @Size(max = 32)
        String locale,
        @Size(max = 20)
        String phone,
        @Size(max = 500)
        String bio,
        @Min(0) @Max(2)
        Integer gender,
        LocalDate birthday,
        String theme,
        Boolean notificationEnabled,
        UserStatus status
) {
}
