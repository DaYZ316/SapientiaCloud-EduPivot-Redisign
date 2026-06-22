package com.dayz.sc.gateway.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHttpClientConfigurationTest {

    @Test
    void shouldDisableReadTimeoutForStreamingResponses() throws Exception {
        var factory = new GatewayHttpClientConfiguration().gatewayClientHttpRequestFactory();

        RequestConfig requestConfig = mergeRequestConfig((HttpComponentsClientHttpRequestFactory) factory);

        assertThat(requestConfig.getResponseTimeout()).isEqualTo(Timeout.DISABLED);
    }

    private RequestConfig mergeRequestConfig(HttpComponentsClientHttpRequestFactory factory) throws Exception {
        Method method = HttpComponentsClientHttpRequestFactory.class
                .getDeclaredMethod("mergeRequestConfig", RequestConfig.class);
        method.setAccessible(true);
        return (RequestConfig) method.invoke(factory, RequestConfig.DEFAULT);
    }
}
