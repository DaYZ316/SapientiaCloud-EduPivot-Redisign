package com.dayz.sc.common.security.filter;

import com.dayz.sc.common.security.support.GatewayJwt;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 Gateway 注入的 {@code X-User-Id} 和 {@code X-User-Role} 请求头创建安全上下文
 * <p>
 * 仅当请求来自可信 Gateway（内网）时启用此 Filter，它跳过 JWT 签名验证，
 * 直接将 Gateway 注入的用户信息构建为 {@link GatewayJwt} 并设置到 SecurityContext 中
 * <p>
 * <b>安全前提：</b>下游服务仅通过内网访问，外部流量必须经过 Gateway
 *
 * @author DaYZ
 * @since 2026-06-11
 */
public class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        GatewayJwt jwt = GatewayJwt.fromHeaders(
                request.getHeader(HEADER_USER_ID),
                request.getHeader(HEADER_USER_ROLE)
        );

        if (jwt != null) {
            var auth = new UsernamePasswordAuthenticationToken(
                    jwt, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
