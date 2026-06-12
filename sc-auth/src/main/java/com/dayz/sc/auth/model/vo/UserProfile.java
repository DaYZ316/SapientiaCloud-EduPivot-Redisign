package com.dayz.sc.auth.model.vo;

import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 返回给客户端的安全用户资料字段。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public record UserProfile(
        UUID id,
        @Nullable
        String email,
        boolean emailVerified,
        @Nullable
        String displayName,
        @Nullable
        String avatarUrl,
        @Nullable
        UUID avatarFileId,
        @Nullable
        String locale,
        UserStatus status,
        @Nullable
        String phone,
        @Nullable
        String bio,
        @Nullable
        Integer gender,
        @Nullable
        LocalDate birthday,
        @Nullable
        String theme,
        boolean notificationEnabled,
        @Nullable
        OauthProvider createdProvider,
        @Nullable
        String createdIp,
        @Nullable
        OauthProvider lastLoginProvider,
        @Nullable
        String lastLoginIp,
        long loginCount,
        @Nullable
        Instant createdAt,
        @Nullable
        Instant updatedAt,
        @Nullable
        Instant lastLoginAt,
        List<OauthProvider> linkedProviders,
        @Nullable
        Integer role,
        @Nullable
        StudentInfo studentInfo,
        @Nullable
        TeacherInfo teacherInfo
) {
}
