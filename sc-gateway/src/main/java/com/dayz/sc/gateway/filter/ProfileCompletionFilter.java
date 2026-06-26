package com.dayz.sc.gateway.filter;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Blocks authenticated users without a selected role from ordinary business APIs.
 *
 * @author DaYZ
 * @since 2026-06-26
 */
public class ProfileCompletionFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_PATHS = List.of(
            "/api/auth/google/login",
            "/api/auth/github/login",
            "/api/auth/register",
            "/api/auth/password/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/users/me",
            "/api/auth/users/me/onboarding",
            "/api/**/internal/**",
            "/internal/**",
            "/actuator/health",
            "/actuator/health/**",
            "/doc.html",
            "/scalar",
            "/scalar/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/webjars/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/openapi/**",
            "/error"
    );

    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ProfileCompletionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (isAllowed(request) || isProfileComplete()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(ErrorCodes.PROFILE_INCOMPLETE.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(ErrorCodes.PROFILE_INCOMPLETE));
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return ALLOWED_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isProfileComplete() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return true;
        }
        Object role = jwt.getClaims().get("role");
        if (role == null || role.toString().isBlank()) {
            return false;
        }
        Object profileComplete = jwt.getClaims().get("profileComplete");
        return !(profileComplete instanceof Boolean completed) || completed;
    }
}
