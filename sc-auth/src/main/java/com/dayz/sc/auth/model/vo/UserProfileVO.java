package com.dayz.sc.auth.model.vo;

import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public record UserProfileVO(
        @JsonProperty("id") UUID id,
        @JsonProperty("email") @Nullable
        String email,
        @JsonProperty("emailVerified") Boolean emailVerified,
        @JsonProperty("displayName") @Nullable
        String displayName,
        @JsonProperty("avatarUrl") @Nullable
        String avatarUrl,
        @JsonProperty("avatarFileId") @Nullable
        UUID avatarFileId,
        @JsonProperty("locale") @Nullable
        String locale,
        @JsonProperty("status") UserStatus status,
        @JsonProperty("phone") @Nullable
        String phone,
        @JsonProperty("bio") @Nullable
        String bio,
        @JsonProperty("gender") @Nullable
        Integer gender,
        @JsonProperty("birthday") @Nullable
        LocalDate birthday,
        @JsonProperty("theme") @Nullable
        String theme,
        @JsonProperty("notificationEnabled") Boolean notificationEnabled,
        @JsonProperty("createdProvider") @Nullable
        OauthProvider createdProvider,
        @JsonProperty("createdIp") @Nullable
        String createdIp,
        @JsonProperty("lastLoginProvider") @Nullable
        OauthProvider lastLoginProvider,
        @JsonProperty("lastLoginIp") @Nullable
        String lastLoginIp,
        @JsonProperty("loginCount") Long loginCount,
        @JsonProperty("createdAt") @Nullable
        Instant createdAt,
        @JsonProperty("updatedAt") @Nullable
        Instant updatedAt,
        @JsonProperty("lastLoginAt") @Nullable
        Instant lastLoginAt,
        @JsonProperty("linkedProviders") List<OauthProvider> linkedProviders,
        @JsonProperty("role") @Nullable
        Integer role,
        @JsonProperty("studentInfo") @Nullable
        StudentInfoVO studentInfo,
        @JsonProperty("teacherInfo") @Nullable
        TeacherInfoVO teacherInfo
) {
}
