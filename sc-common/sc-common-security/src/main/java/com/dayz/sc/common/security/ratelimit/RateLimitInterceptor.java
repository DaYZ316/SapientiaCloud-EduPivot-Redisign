package com.dayz.sc.common.security.ratelimit;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 限流拦截器。
 * <p>
 * 检查 Controller 方法上的 {@link RateLimited} 注解，基于客户端 IP 进行滑动窗口限流
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public record RateLimitInterceptor(RateLimiterService rateLimiterService) implements HandlerInterceptor {

    private static final String UNKNOWN_IP = "unknown";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null) {
            return true;
        }

        String clientIp = resolveClientIp(request);
        String key = request.getRequestURI() + ":" + clientIp;

        if (!rateLimiterService.tryAcquire(key, rateLimited.windowSeconds(), rateLimited.maxRequests())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "请求过于频繁，请稍后再试");
        }

        return true;
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
