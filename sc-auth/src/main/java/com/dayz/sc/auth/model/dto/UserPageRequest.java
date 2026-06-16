package com.dayz.sc.auth.model.dto;

import com.dayz.sc.auth.model.enums.UserStatus;

/**
 * 用户分页查询参数
 *
 * @author DaYZ
 * @since 2026-05-08
 */
public record UserPageRequest(
        Long page,
        Long size,
        String keyword,
        UserStatus status,
        Integer role
) {
}
