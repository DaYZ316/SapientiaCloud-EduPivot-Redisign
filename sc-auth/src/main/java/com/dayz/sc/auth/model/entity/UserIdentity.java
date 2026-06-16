package com.dayz.sc.auth.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dayz.sc.auth.model.enums.OauthProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 绑定到智语·云枢系统用户的 OAuth 第三方身份
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_user_identities")
public class UserIdentity {
    @TableId(value = "id", type = IdType.INPUT)
    private UUID id;
    @TableField("user_id")
    private UUID userId;
    private OauthProvider provider;
    @TableField("provider_user_id")
    private String providerUserId;
    @TableField("provider_login")
    private String providerLogin;
    @TableField("provider_email")
    private String providerEmail;
    @TableField("provider_email_verified")
    private Boolean providerEmailVerified;
    @TableField("provider_display_name")
    private String providerDisplayName;
    @TableField("provider_avatar_url")
    private String providerAvatarUrl;
    @TableField("linked_at")
    private Instant linkedAt;
    @TableField("last_login_at")
    private Instant lastLoginAt;
}
