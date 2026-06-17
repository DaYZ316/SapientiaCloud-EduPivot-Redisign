package com.dayz.sc.auth.controller;

import com.dayz.sc.auth.model.dto.*;
import com.dayz.sc.auth.model.vo.LoginResponseVO;
import com.dayz.sc.auth.service.GitHubLoginService;
import com.dayz.sc.auth.service.GoogleLoginService;
import com.dayz.sc.auth.service.PasswordLoginService;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.service.JwtTokenService;
import com.dayz.sc.common.security.token.RefreshTokenService;
import com.dayz.sc.common.security.token.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证接口
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String UNKNOWN_IP = "unknown";

    private final GoogleLoginService googleLoginService;
    private final GitHubLoginService gitHubLoginService;
    private final PasswordLoginService passwordLoginService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenService jwtTokenService;
    private final JwtDecoder jwtDecoder;

    @PostMapping("/google/login")
    @RateLimited
    public ApiResponse<@NonNull LoginResponseVO> googleLogin(@Valid @RequestBody GoogleLoginRequest request,
                                                             HttpServletRequest servletRequest) {
        return ApiResponse.ok(googleLoginService.login(request, resolveClientIp(servletRequest)));
    }

    @PostMapping("/github/login")
    @RateLimited
    public ApiResponse<@NonNull LoginResponseVO> githubLogin(@Valid @RequestBody GitHubLoginRequest request,
                                                             HttpServletRequest servletRequest) {
        return ApiResponse.ok(gitHubLoginService.login(request, resolveClientIp(servletRequest)));
    }

    @PostMapping("/register")
    @RateLimited(maxRequests = 5, windowSeconds = 300)
    public ApiResponse<@NonNull LoginResponseVO> register(@Valid @RequestBody RegisterRequest request,
                                                          HttpServletRequest servletRequest) {
        return ApiResponse.ok(passwordLoginService.register(request, resolveClientIp(servletRequest)));
    }

    @PostMapping("/password/login")
    @RateLimited
    public ApiResponse<@NonNull LoginResponseVO> passwordLogin(@Valid @RequestBody PasswordLoginRequest request,
                                                               HttpServletRequest servletRequest) {
        return ApiResponse.ok(passwordLoginService.login(request, resolveClientIp(servletRequest)));
    }

    /**
     * 刷新 Access Token。
     * <p>
     * 使用 Refresh Token 换取新的 Access Token + 新的 Refresh Token（轮转）。
     */
    @PostMapping("/refresh")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull LoginResponseVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        String oldRefreshToken = request.refreshToken();

        String userId = refreshTokenService.validateRefreshToken(oldRefreshToken);
        if (userId == null) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "Refresh Token 无效或已过期");
        }

        Integer role = refreshTokenService.getRoleFromToken(oldRefreshToken);

        // 轮转 Refresh Token
        String newRefreshToken = refreshTokenService.rotateRefreshToken(oldRefreshToken, userId, role);
        if (newRefreshToken == null) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "Refresh Token 无效");
        }

        // 签发新 Access Token
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", userId);
        if (role != null) {
            claims.put("role", role);
        }
        String newAccessToken = jwtTokenService.createAccessToken(userId, claims);

        return ApiResponse.ok(new LoginResponseVO(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtTokenService.getAccessTokenTtlSeconds(),
                null
        ));
    }

    /**
     * 登出。
     * <p>
     * 将当前 Access Token 加入黑名单，并吊销 Refresh Token。
     */
    @PostMapping("/logout")
    @RateLimited
    public ApiResponse<@NonNull Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestBody(required = false) LogoutRequest request) {
        // 从 Authorization header 提取 token 并加入黑名单
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            String accessToken = authorization.substring(BEARER_PREFIX.length());
            try {
                Jwt jwt = jwtDecoder.decode(accessToken);
                String jti = jwt.getId();
                if (jti != null && jwt.getExpiresAt() != null) {
                    long ttlSeconds = jwt.getExpiresAt().getEpochSecond() - System.currentTimeMillis() / 1000;
                    if (ttlSeconds > 0) {
                        tokenBlacklistService.blacklist(jti, ttlSeconds);
                    }
                }
            } catch (Exception e) {
                // token 解析失败不阻止登出
            }
        }

        // 吊销 Refresh Token
        if (request != null && StringUtils.hasText(request.refreshToken())) {
            refreshTokenService.revokeRefreshToken(request.refreshToken());
        }

        return ApiResponse.ok(null);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String firstIp = forwardedFor.split(",", 2)[0].trim();
            if (StringUtils.hasText(firstIp) && !UNKNOWN_IP.equalsIgnoreCase(firstIp)) {
                return firstIp;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp) && !UNKNOWN_IP.equalsIgnoreCase(realIp.trim())) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
