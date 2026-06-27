package com.dayz.sc.gateway.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Gateway 后端转发 HTTP 客户端配置
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Configuration
public class GatewayHttpClientConfiguration {

    private static final int MAX_TOTAL_CONNECTIONS = 200;
    private static final int MAX_CONNECTIONS_PER_ROUTE = 100;
    private static final Timeout CONNECTION_REQUEST_TIMEOUT = Timeout.ofSeconds(2);
    private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(3);
    /**
     * SSE and AI streaming endpoints can wait indefinitely between chunks.
     */
    private static final Timeout RESPONSE_TIMEOUT = Timeout.DISABLED;
    private static final TimeValue IDLE_CONNECTION_TTL = TimeValue.ofSeconds(30);

    @SuppressWarnings("deprecation")
    @Bean
    public ClientHttpRequestFactory gatewayClientHttpRequestFactory() {
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(MAX_TOTAL_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setResponseTimeout(RESPONSE_TIMEOUT)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(IDLE_CONNECTION_TTL)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT.toMillisecondsIntBound());
        requestFactory.setReadTimeout(RESPONSE_TIMEOUT.toMillisecondsIntBound());
        return requestFactory;
    }
}
