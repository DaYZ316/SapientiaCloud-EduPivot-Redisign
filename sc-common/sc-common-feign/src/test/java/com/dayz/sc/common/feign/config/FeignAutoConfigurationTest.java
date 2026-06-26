package com.dayz.sc.common.feign.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignAutoConfigurationTest {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";

    private final RequestInterceptor interceptor = new FeignAutoConfiguration().bearerTokenRelayRequestInterceptor();

    @AfterEach
    void clearRequestAttributes() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void bearerTokenRelayRequestInterceptor_shouldRelayHeadersForInternalClients() {
        setIncomingHeaders();
        RequestTemplate template = templateFor("sc-course");

        interceptor.apply(template);

        assertThat(template.headers())
                .containsEntry(HttpHeaders.AUTHORIZATION, values("Bearer local-token"))
                .containsEntry(HEADER_USER_ID, values("user-1"))
                .containsEntry(HEADER_USER_ROLE, values("STUDENT"));
    }

    @Test
    void bearerTokenRelayRequestInterceptor_shouldSkipExternalOauthClients() {
        setIncomingHeaders();
        RequestTemplate template = templateFor("github-oauth-client");

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKeys(
                HttpHeaders.AUTHORIZATION,
                HEADER_USER_ID,
                HEADER_USER_ROLE
        );
    }

    private void setIncomingHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer local-token");
        when(request.getHeader(HEADER_USER_ID)).thenReturn("user-1");
        when(request.getHeader(HEADER_USER_ROLE)).thenReturn("STUDENT");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private RequestTemplate templateFor(String clientName) {
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(new Target.HardCodedTarget<>(Map.class, clientName, "http://" + clientName));
        return template;
    }

    private Collection<String> values(String value) {
        return java.util.List.of(value);
    }
}
