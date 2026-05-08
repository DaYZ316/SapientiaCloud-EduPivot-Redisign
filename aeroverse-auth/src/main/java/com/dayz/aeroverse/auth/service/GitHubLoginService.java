package com.dayz.aeroverse.auth.service;

import com.dayz.aeroverse.auth.client.github.GitHubOauthClient;
import com.dayz.aeroverse.auth.client.github.GitHubUserClient;
import com.dayz.aeroverse.auth.client.github.dto.GitHubEmailResponse;
import com.dayz.aeroverse.auth.client.github.dto.GitHubTokenResponse;
import com.dayz.aeroverse.auth.client.github.dto.GitHubUserResponse;
import com.dayz.aeroverse.auth.config.GitHubOauthProperties;
import com.dayz.aeroverse.auth.model.dto.GitHubLoginRequest;
import com.dayz.aeroverse.auth.model.dto.OauthUserInfo;
import com.dayz.aeroverse.auth.model.entity.User;
import com.dayz.aeroverse.auth.model.enums.OauthProvider;
import com.dayz.aeroverse.auth.model.vo.LoginResponse;
import com.dayz.aeroverse.auth.model.vo.UserProfile;
import com.dayz.aeroverse.common.error.BusinessException;
import com.dayz.aeroverse.common.error.ErrorCodes;
import com.dayz.aeroverse.common.security.config.JwtProperties;
import com.dayz.aeroverse.common.security.service.JwtTokenService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 处理 GitHub OAuth 授权码登录并签发 AeroVerse JWT 访问令牌。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Service
@RequiredArgsConstructor
public class GitHubLoginService {
    private static final String GITHUB_JSON = "application/vnd.github+json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE = "Bearer";
    private static final int EMAIL_PAGE_SIZE = 100;
    private static final int FIRST_PAGE = 1;

    private final GitHubOauthClient gitHubOauthClient;
    private final GitHubUserClient gitHubUserClient;
    private final GitHubOauthProperties gitHubOauthProperties;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final UserAccountService userAccountService;

    public LoginResponse login(GitHubLoginRequest request, String clientIp) {
        assertGitHubConfigured();
        String redirectUri = resolveRedirectUri(request);

        GitHubTokenResponse token;
        try {
            token = gitHubOauthClient.exchangeCode(
                    MediaType.APPLICATION_JSON_VALUE,
                    gitHubOauthProperties.getClientId(),
                    gitHubOauthProperties.getClientSecret(),
                    request.code(),
                    redirectUri,
                    normalize(request.codeVerifier())
            );
        } catch (FeignException ex) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 授权码换取令牌失败");
        }

        if (token == null || !token.successful()) {
            String message = token == null ? "GitHub 登录失败" : "GitHub 登录失败: " + token.errorDescription();
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, message);
        }

        String authorization = BEARER_PREFIX + token.accessToken();
        GitHubUserResponse userInfo;
        try {
            userInfo = gitHubUserClient.getUser(authorization, GITHUB_JSON, gitHubOauthProperties.getApiVersion());
        } catch (FeignException ex) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 用户信息读取失败");
        }
        if (userInfo == null || userInfo.id() == null) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 用户信息缺少 id");
        }

        GitHubEmailResponse email = resolvePrimaryEmail(authorization, userInfo);
        UserAccountService.AuthenticatedUser authenticatedUser = userAccountService.loginWithOauth(toOauthUserInfo(userInfo, email), clientIp);
        User user = authenticatedUser.user();
        UserProfile profile = toUserProfile(authenticatedUser);
        String accessToken = jwtTokenService.createAccessToken(user.getId().toString(), buildClaims(user, userInfo));

        return new LoginResponse(accessToken, TOKEN_TYPE, jwtProperties.getTtl().toSeconds(), profile);
    }

    private GitHubEmailResponse resolvePrimaryEmail(String authorization, GitHubUserResponse userInfo) {
        List<GitHubEmailResponse> emails = List.of();
        try {
            emails = gitHubUserClient.listEmails(
                    authorization,
                    GITHUB_JSON,
                    gitHubOauthProperties.getApiVersion(),
                    EMAIL_PAGE_SIZE,
                    FIRST_PAGE
            );
        } catch (FeignException ex) {
            if (ex.status() != 403 && ex.status() != 404) {
                throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 邮箱信息读取失败");
            }
            // 未授权 user:email scope 时，GitHub 仍可能在 /user 返回公开邮箱。
        }

        return selectEmail(emails)
                .orElseGet(() -> StringUtils.hasText(userInfo.email())
                        ? new GitHubEmailResponse(userInfo.email(), true, false, null)
                        : null);
    }

    private Optional<GitHubEmailResponse> selectEmail(List<GitHubEmailResponse> emails) {
        if (emails == null || emails.isEmpty()) {
            return Optional.empty();
        }

        return emails.stream()
                .filter(email -> StringUtils.hasText(email.email()))
                .filter(email -> Boolean.TRUE.equals(email.verified()))
                .max(Comparator.comparing((GitHubEmailResponse email) -> Boolean.TRUE.equals(email.primary()))
                        .thenComparing(email -> StringUtils.hasText(email.visibility())));
    }

    private OauthUserInfo toOauthUserInfo(GitHubUserResponse userInfo, GitHubEmailResponse email) {
        return new OauthUserInfo(
                OauthProvider.GITHUB,
                userInfo.id().toString(),
                userInfo.login(),
                email == null ? null : email.email(),
                email != null && Boolean.TRUE.equals(email.verified()),
                resolveDisplayName(userInfo),
                userInfo.avatarUrl(),
                null
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

    private Map<String, Object> buildClaims(User user, GitHubUserResponse userInfo) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("authProvider", OauthProvider.GITHUB.name());
        claims.put("providerUserId", userInfo.id().toString());
        claims.put("status", user.getStatus().name());
        claims.put("loginCount", user.getLoginCount());
        putProviderIfPresent(claims, "createdProvider", user.getCreatedProvider());
        putProviderIfPresent(claims, "lastLoginProvider", user.getLastLoginProvider());
        putIfHasText(claims, "providerLogin", userInfo.login());
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

    private String resolveDisplayName(GitHubUserResponse userInfo) {
        return StringUtils.hasText(userInfo.name()) ? userInfo.name() : userInfo.login();
    }

    private void putIfHasText(Map<String, Object> claims, String key, String value) {
        if (StringUtils.hasText(value)) {
            claims.put(key, value);
        }
    }

    private void assertGitHubConfigured() {
        if (!StringUtils.hasText(gitHubOauthProperties.getClientId())
                || !StringUtils.hasText(gitHubOauthProperties.getClientSecret())) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub OAuth clientId/clientSecret 未配置");
        }
    }

    private String resolveRedirectUri(GitHubLoginRequest request) {
        String redirectUri = StringUtils.hasText(request.redirectUri())
                ? request.redirectUri()
                : gitHubOauthProperties.getRedirectUri();
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub OAuth redirectUri 未配置");
        }
        return redirectUri;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
