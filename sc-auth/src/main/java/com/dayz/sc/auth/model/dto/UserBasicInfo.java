package com.dayz.sc.auth.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * 用户基本信息 DTO，用于服务间通信
 *
 * @author DaYZ
 * @since 2026-06-11
 */
public record UserBasicInfo(
        @JsonProperty UUID id,
        @JsonProperty @Nullable String displayName,
        @JsonProperty @Nullable String avatarUrl,
        @JsonProperty @Nullable Integer role
) {}
