package com.dayz.sc.common.web.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class ApiRequestDebugLoggingFilterTest {

    private final ApiRequestDebugLoggingFilter filter = new ApiRequestDebugLoggingFilter();

    @BeforeEach
    void setUp() {
        ((Logger) LoggerFactory.getLogger(ApiRequestDebugLoggingFilter.class)).setLevel(Level.DEBUG);
    }

    @Test
    void shouldLogApiRequestAndMaskSensitiveQuery(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/live-practices/subscribe");
        request.setQueryString("courseId=1&token=secret");
        request.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(output).contains("API request method=GET path=/api/live-practices/subscribe");
        assertThat(output).contains("query=courseId=1&token=***");
        assertThat(output).contains("status=200");
        assertThat(output).contains("userId=user-1");
        assertThat(output).doesNotContain("secret");
    }

    @Test
    void shouldSkipNonApiRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
