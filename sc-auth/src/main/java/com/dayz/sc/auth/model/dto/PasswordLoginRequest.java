package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 账号密码登录请求
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public record PasswordLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
