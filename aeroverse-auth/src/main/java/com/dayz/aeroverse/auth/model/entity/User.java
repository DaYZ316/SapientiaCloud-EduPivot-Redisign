package com.dayz.aeroverse.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dayz.aeroverse.auth.model.enums.OauthProvider;
import com.dayz.aeroverse.auth.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 灵境航途内部系统用户账号。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_users")
public class User {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    private String email;
    @TableField("email_verified")
    private boolean emailVerified;
    @TableField("display_name")
    private String displayName;
    @TableField("avatar_url")
    private String avatarUrl;
    private String locale;
    private UserStatus status;
    @TableField("created_at")
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;
    @TableField("last_login_at")
    private Instant lastLoginAt;
    @TableField("created_provider")
    private OauthProvider createdProvider;
    @TableField("created_ip")
    private String createdIp;
    @TableField("last_login_provider")
    private OauthProvider lastLoginProvider;
    @TableField("last_login_ip")
    private String lastLoginIp;
    @TableField("login_count")
    private long loginCount;
}
