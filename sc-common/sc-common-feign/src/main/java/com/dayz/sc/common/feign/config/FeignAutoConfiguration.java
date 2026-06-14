package com.dayz.sc.common.feign.config;

import com.dayz.sc.common.feign.client.AuthInternalClientFallback;
import com.dayz.sc.common.feign.client.StorageInternalClientFallback;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 启用智语·云枢基础包下的 OpenFeign 客户端。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@AutoConfiguration
@EnableFeignClients(basePackages = "com.dayz.sc")
@Import({AuthInternalClientFallback.class, StorageInternalClientFallback.class})
public class FeignAutoConfiguration {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    @Bean
    public RequestInterceptor bearerTokenRelayRequestInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                HttpServletRequest request = attributes.getRequest();
                String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && !authorization.isBlank()) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
                String userId = request.getHeader(HEADER_USER_ID);
                String userRole = request.getHeader(HEADER_USER_ROLE);
                if (userId != null && !userId.isBlank()) {
                    template.header(HEADER_USER_ID, userId);
                }
                if (userRole != null && !userRole.isBlank()) {
                    template.header(HEADER_USER_ROLE, userRole);
                }
            }
        };
    }
}
