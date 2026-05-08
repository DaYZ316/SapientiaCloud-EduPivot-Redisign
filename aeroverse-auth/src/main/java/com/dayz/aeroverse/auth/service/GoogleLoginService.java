package com.dayz.aeroverse.auth.service;

import com.dayz.aeroverse.auth.client.google.GoogleOauthClient;
import com.dayz.aeroverse.auth.client.google.GoogleUserInfoClient;
import com.dayz.aeroverse.auth.client.google.dto.GoogleTokenResponse;
import com.dayz.aeroverse.auth.client.google.dto.GoogleUserInfoResponse;
import com.dayz.aeroverse.auth.config.GoogleOauthProperties;
import com.dayz.aeroverse.auth.model.dto.GoogleLoginRequest;
import com.dayz.aeroverse.auth.model.dto.OauthUserInfo;
import com.dayz.aeroverse.auth.model.entity.User;
import com.dayz.aeroverse.auth.model.enums.OauthProvider;
import com.dayz.aeroverse.auth.model.vo.LoginResponse;
import com.dayz.aeroverse.auth.model.vo.UserProfile;
import com.dayz.aeroverse.common.error.BusinessException;
import com.dayz.aeroverse.common.error.ErrorCodes;
import com.dayz.aeroverse.common.security.config.JwtProperties;
import com.dayz.aeroverse.common.security.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 处理 Google OAuth2 授权码登录并签发 AeroVerse JWT 访问令牌。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Service
@RequiredArgsConstructor
public class GoogleLoginService {
    private static final String AUTHORIZATION_CODE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE = "Bearer";

    private final GoogleOauthClient googleOauthClient;
    private final GoogleUserInfoClient googleUserInfoClient;
    private final GoogleOauthProperties googleOauthProperties;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final UserAccountService userAccountService;

    public LoginResponse login(GoogleLoginRequest request, String clientIp) {
        assertGoogleConfigured();
        String redirectUri = resolveRedirectUri(request);

        GoogleTokenResponse token = googleOauthClient.exchangeCode(
                request.code(),
                googleOauthProperties.getClientId(),
                googleOauthProperties.getClientSecret(),
                redirectUri,
                AUTHORIZATION_CODE
        );

        if (token == null || !token.successful()) {
            String message = token == null ? "Google 登录失败" : "Google 登录失败: " + token.errorDescription();
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, message);
        }

        GoogleUserInfoResponse userInfo = googleUserInfoClient.getUserInfo(BEARER_PREFIX + token.accessToken());
        if (userInfo == null || !StringUtils.hasText(userInfo.sub())) {
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, "Google 用户信息缺少 subject");
        }

        UserAccountService.AuthenticatedUser authenticatedUser = userAccountService.loginWithOauth(toOauthUserInfo(userInfo), clientIp);
        User user = authenticatedUser.user();
        UserProfile profile = toUserProfile(authenticatedUser);
        String accessToken = jwtTokenService.createAccessToken(user.getId().toString(), buildClaims(user, userInfo));

        return new LoginResponse(accessToken, TOKEN_TYPE, jwtProperties.getTtl().toSeconds(), profile);
    }

    private OauthUserInfo toOauthUserInfo(GoogleUserInfoResponse userInfo) {
        return new OauthUserInfo(
                OauthProvider.GOOGLE,
                userInfo.sub(),
                null,
                userInfo.email(),
                Boolean.TRUE.equals(userInfo.emailVerified()),
                userInfo.name(),
                userInfo.picture(),
                userInfo.locale()
        );
    }

    private UserProfile toUserProfile(UserAccountService.AuthenticatedUser authenticatedUser) {
        User user = authenticatedUser.user();
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getLocale(),
                user.getStatus(),
                user.getCreatedProvider(),
                user.getCreatedIp(),
                user.getLastLoginProvider(),
                user.getLastLoginIp(),
                user.getLoginCount(),
                authenticatedUser.linkedProviders()
        );
    }

    private Map<String, Object> buildClaims(User user, GoogleUserInfoResponse userInfo) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("authProvider", OauthProvider.GOOGLE.name());
        claims.put("providerUserId", userInfo.sub());
        claims.put("status", user.getStatus().name());
        claims.put("loginCount", user.getLoginCount());
        putProviderIfPresent(claims, "createdProvider", user.getCreatedProvider());
        putProviderIfPresent(claims, "lastLoginProvider", user.getLastLoginProvider());
        putIfHasText(claims, "email", user.getEmail());
        putIfHasText(claims, "displayName", user.getDisplayName());
        putIfHasText(claims, "avatarUrl", user.getAvatarUrl());
        return claims;
    }

    private void putProviderIfPresent(Map<String, Object> claims, String key, OauthProvider provider) {
        if (provider != null) {
            claims.put(key, provider.name());
        }
    }

    private void putIfHasText(Map<String, Object> claims, String key, String value) {
        if (StringUtils.hasText(value)) {
            claims.put(key, value);
        }
    }

    private void assertGoogleConfigured() {
        if (!StringUtils.hasText(googleOauthProperties.getClientId())
                || !StringUtils.hasText(googleOauthProperties.getClientSecret())) {
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, "Google OAuth2 clientId/clientSecret 未配置");
        }
    }

    private String resolveRedirectUri(GoogleLoginRequest request) {
        String redirectUri = StringUtils.hasText(request.redirectUri())
                ? request.redirectUri()
                : googleOauthProperties.getRedirectUri();
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, "Google OAuth2 redirectUri 未配置");
        }
        return redirectUri;
    }
}
