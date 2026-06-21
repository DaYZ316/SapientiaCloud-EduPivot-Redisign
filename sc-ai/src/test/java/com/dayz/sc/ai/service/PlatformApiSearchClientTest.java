package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformApiSearchClientTest {

    private HttpServer server;
    private AiProperties properties;
    private PlatformApiSearchClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        properties = new AiProperties();
        properties.getAgentSearch().setGatewayBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.getAgentSearch().setTimeout(Duration.ofSeconds(2));
        properties.getAgentSearch().setMaxResponseChars(200);
        client = new PlatformApiSearchClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void queryShouldAllowRegisteredGetEndpointWithAuthorization() {
        server.createContext("/openapi/auth", exchange -> respond(exchange, 200, """
                {"paths":{"/api/auth/users/me":{"get":{"summary":"Current user"}}}}
                """));
        server.createContext("/api/auth/users/me", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            respond(exchange, authorization == null ? 401 : 200, "{\"displayName\":\"Li Wenhao\"}");
        });

        var item = client.query("auth", "/api/auth/users/me", Map.of(), "Bearer token");

        assertThat(item.sourceType()).isEqualTo("PLATFORM_API");
        assertThat(item.title()).isEqualTo("Current user");
        assertThat(item.snippet()).contains("Li Wenhao");
        assertThat(item.indexInfo())
                .containsEntry("service", "auth")
                .containsEntry("path", "/api/auth/users/me")
                .containsEntry("method", "GET")
                .containsEntry("queryParams", Map.of());
        assertThat(item.indexInfo().toString()).doesNotContain("Bearer token", "authorization", "Authorization");
    }

    @Test
    void queryShouldRejectUnregisteredEndpoint() {
        server.createContext("/openapi/auth", exchange -> respond(exchange, 200, """
                {"paths":{"/api/auth/users/me":{"get":{"summary":"Current user"}}}}
                """));

        var item = client.query("auth", "/api/auth/users/all", Map.of(), "Bearer token");

        assertThat(item.sourceType()).isEqualTo("PLATFORM_API_ERROR");
        assertThat(item.snippet()).contains("未在平台 OpenAPI 中登记");
    }

    @Test
    void queryShouldReportOpenApiUnavailableWhenDocumentHasNoOperations() {
        server.createContext("/openapi/auth", exchange -> respond(exchange, 200, """
                {"paths":{}}
                """));

        var item = client.query("auth", "/api/auth/users/me", Map.of(), "Bearer token");

        assertThat(item.sourceType()).isEqualTo("PLATFORM_API_ERROR");
        assertThat(item.snippet()).contains("OpenAPI 暂时无法读取");
    }

    @Test
    void queryShouldRejectInternalEndpoint() {
        var item = client.query("auth", "/api/auth/users/internal/basic", Map.of(), "Bearer token");

        assertThat(item.sourceType()).isEqualTo("PLATFORM_API_ERROR");
        assertThat(item.snippet()).contains("不允许");
    }

    @Test
    void queryShouldConvertForbiddenToUserMessage() {
        server.createContext("/openapi/course", exchange -> respond(exchange, 200, """
                {"paths":{"/api/courses":{"get":{"summary":"Courses"}}}}
                """));
        server.createContext("/api/courses", exchange -> respond(exchange, 403, "{}"));

        var item = client.query("course", "/api/courses", Map.of("page", 1), "Bearer token");

        assertThat(item.sourceType()).isEqualTo("PLATFORM_API_ERROR");
        assertThat(item.snippet()).contains("没有权限");
    }

    @Test
    void queryShouldRejectOversizedResponse() {
        properties.getAgentSearch().setMaxResponseChars(20);
        server.createContext("/openapi/ai", exchange -> respond(exchange, 200, """
                {"paths":{"/api/ai/conversations":{"get":{"summary":"Conversations"}}}}
                """));
        server.createContext("/api/ai/conversations", exchange -> respond(exchange, 200, """
                {"data":"abcdefghijklmnopqrstuvwxyz"}
                """));

        var item = client.query("ai", "/api/ai/conversations", Map.of(), "Bearer token");

        assertThat(item.sourceType()).isEqualTo("PLATFORM_API_ERROR");
        assertThat(item.snippet()).contains("过长");
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
