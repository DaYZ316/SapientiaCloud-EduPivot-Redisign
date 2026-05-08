package com.dayz.aeroverse.auth.controller;

import com.dayz.aeroverse.auth.model.dto.GitHubLoginRequest;
import com.dayz.aeroverse.auth.model.dto.GoogleLoginRequest;
import com.dayz.aeroverse.auth.model.vo.LoginResponse;
import com.dayz.aeroverse.auth.service.GitHubLoginService;
import com.dayz.aeroverse.auth.service.GoogleLoginService;
import com.dayz.aeroverse.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final GoogleLoginService googleLoginService;
    private final GitHubLoginService gitHubLoginService;

    @PostMapping("/google/login")
    public ApiResponse<LoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request,
                                                  HttpServletRequest servletRequest) {
        return ApiResponse.ok(googleLoginService.login(request, resolveClientIp(servletRequest)));
    }

    @PostMapping("/github/login")
    public ApiResponse<LoginResponse> githubLogin(@Valid @RequestBody GitHubLoginRequest request,
                                                  HttpServletRequest servletRequest) {
        return ApiResponse.ok(gitHubLoginService.login(request, resolveClientIp(servletRequest)));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String firstIp = forwardedFor.split(",", 2)[0].trim();
            if (StringUtils.hasText(firstIp) && !"unknown".equalsIgnoreCase(firstIp)) {
                return firstIp;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp) && !"unknown".equalsIgnoreCase(realIp.trim())) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
