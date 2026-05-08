package com.dayz.aeroverse.auth.model.dto;

import com.dayz.aeroverse.auth.model.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 用户管理页面提交的账号资料更新参数。
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
        @Size(max = 32)
        String locale,
        UserStatus status
) {
}
