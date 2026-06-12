package com.dayz.sc.auth.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 账号注册请求。
 *
 * @param email       邮箱
 * @param password    密码（8-64 位）
 * @param displayName 昵称（可选，默认取邮箱前缀）
 * @param role        角色：1=学生，2=教师
 * @author DaYZ
 * @since 2026-06-09
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        String displayName,
        @Min(1) @Max(2) int role
) {
}
