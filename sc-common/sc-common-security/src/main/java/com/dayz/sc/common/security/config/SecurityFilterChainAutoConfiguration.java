package com.dayz.sc.common.security.config;

import com.dayz.sc.common.security.filter.GatewayHeaderAuthenticationFilter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 后端服务通用 SecurityFilterChain 自动配置
 * <p>
 * 提供两种认证模式：
 * <ul>
 *   <li>JWT 模式（默认）：通过 oauth2ResourceServer 验证 JWT 签名</li>
 *   <li>Gateway 信任模式：信任 Gateway 注入的 X-User-Id/X-User-Role 请求头，跳过 JWT 验证</li>
 * </ul>
 * <p>
 * 各服务可通过定义自己的 {@link SecurityFilterChain} Bean 来覆盖此默认配置
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityFilterChainAutoConfiguration {

    private static final String[] DEFAULT_PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**",
            "/error",
            "/.well-known/jwks.json"
    };

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityEndpointProperties endpointProperties) throws Exception {
        String[] configuredEndpoints = endpointProperties.getPublicEndpoints();
        Set<String> endpoints = new LinkedHashSet<>(Arrays.asList(DEFAULT_PUBLIC_ENDPOINTS));
        endpoints.addAll(Arrays.asList(configuredEndpoints));
        String[] publicEndpoints = endpoints.toArray(String[]::new);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(publicEndpoints).permitAll()
                        .anyRequest().authenticated()
                );

        if (endpointProperties.isTrustGatewayHeaders()) {
            // 信任 Gateway 注入的请求头，跳过 JWT 验证
            http.addFilterBefore(new GatewayHeaderAuthenticationFilter(),
                    UsernamePasswordAuthenticationFilter.class);
        } else {
            // 标准 JWT 验证模式
            http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {
            }));
        }

        return http.build();
    }

    @Bean
    @ConfigurationProperties(prefix = "edupivot.security")
    public SecurityEndpointProperties securityEndpointProperties() {
        return new SecurityEndpointProperties();
    }

    /**
     * 安全端点配置属性
     */
    @Getter
    @Setter
    public static class SecurityEndpointProperties {
        /**
         * 公共（无需认证）端点列表
         */
        private String[] publicEndpoints = {};

        /**
         * 是否信任 Gateway 注入的 X-User-Id/X-User-Role 请求头
         * <p>
         * 启用后跳过 JWT 签名验证，直接从请求头构建用户身份
         * 仅当服务仅通过内网 Gateway 访问时启用
         */
        private boolean trustGatewayHeaders = false;
    }
}
