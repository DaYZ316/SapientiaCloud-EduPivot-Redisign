package com.dayz.sc.common.security.config;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityFilterChainAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(SecurityFilterChainAutoConfiguration.class, TestSecurityConfiguration.class)
            .withPropertyValues("edupivot.security.trust-gateway-headers=true");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPermitAsyncDispatcherRequests() {
        contextRunner.run(context -> {
            AuthorizationFilter authorizationFilter = authorizationFilter(context.getBean(SecurityFilterChain.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/subscribe");
            request.setDispatcherType(DispatcherType.ASYNC);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean reachedApplication = new AtomicBoolean(false);

            authorizationFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                    reachedApplication.set(true));

            assertThat(reachedApplication).isTrue();
        });
    }

    @Test
    void shouldStillRequireAuthenticationForNormalProtectedRequests() {
        contextRunner.run(context -> {
            AuthorizationFilter authorizationFilter = authorizationFilter(context.getBean(SecurityFilterChain.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notifications/subscribe");
            request.setDispatcherType(DispatcherType.REQUEST);
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertThatThrownBy(() -> authorizationFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
            })).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        });
    }

    private AuthorizationFilter authorizationFilter(SecurityFilterChain securityFilterChain) {
        return securityFilterChain.getFilters().stream()
                .filter(AuthorizationFilter.class::isInstance)
                .map(AuthorizationFilter.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @EnableWebSecurity
    static class TestSecurityConfiguration {
    }
}
