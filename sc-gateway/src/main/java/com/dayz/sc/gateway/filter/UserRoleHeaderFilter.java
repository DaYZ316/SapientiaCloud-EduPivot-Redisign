package com.dayz.sc.gateway.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * Gateway 过滤器：从 Spring Security 已认证的 JWT 中提取用户信息，注入请求头传递给下游服务
 * <p>
 * 注入的请求头：
 * <ul>
 *   <li>{@code X-User-Id} — 用户 ID</li>
 *   <li>{@code X-User-Role} — 用户角色</li>
 * </ul>
 * <p>
 * 本过滤器依赖 Spring Security 的 {@code oauth2ResourceServer} 已完成 JWT 验签和黑名单检查，
 * 不再重复调用 {@code JwtDecoder.decode()}
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Component
public class UserRoleHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getSubject();
            Object role = jwt.getClaims().get("role");

            Map<String, String> extraHeaders = new HashMap<>(2);
            if (userId != null) {
                extraHeaders.put("X-User-Id", userId);
            }
            if (role != null) {
                extraHeaders.put("X-User-Role", role.toString());
            }

            chain.doFilter(new HeaderAddingRequestWrapper(httpRequest, extraHeaders), response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 包装请求，添加额外的请求头
     */
    private static class HeaderAddingRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> extraHeaders;

        public HeaderAddingRequestWrapper(HttpServletRequest request, Map<String, String> extraHeaders) {
            super(request);
            this.extraHeaders = extraHeaders;
        }

        @Override
        public String getHeader(String name) {
            String value = extraHeaders.get(name);
            if (value != null) {
                return value;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>(Collections.list(super.getHeaderNames()));
            names.addAll(extraHeaders.keySet());
            return Collections.enumeration(names);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String value = extraHeaders.get(name);
            if (value != null) {
                return Collections.enumeration(Collections.singletonList(value));
            }
            return super.getHeaders(name);
        }
    }
}
