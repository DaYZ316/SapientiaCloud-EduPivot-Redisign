package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WebSearchClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void searchShouldReturnDisabledOrMisconfiguredWhenUnavailable() {
        AiProperties properties = new AiProperties();
        WebSearchClient client = new WebSearchClient(properties);

        properties.getAgentSearch().setWebSearchEnabled(false);
        assertThat(client.search("AI news", 3).status()).isEqualTo(AgentSearchStatus.DISABLED);

        properties.getAgentSearch().setWebSearchEnabled(true);
        assertThat(client.search("AI news", 3).status()).isEqualTo(AgentSearchStatus.MISCONFIGURED);
    }

    @Test
    void searchShouldMapTavilyCompatibleResults() throws Exception {
        startServer("""
                {
                  "results": [
                    {
                      "title": "AI News",
                      "url": "https://example.com/ai",
                      "content": "This is a long AI news snippet that should be shortened.",
                      "score": 0.92,
                      "published_date": "2026-06-24"
                    }
                  ]
                }
                """);
        AiProperties properties = webProperties();
        properties.getAgentSearch().setWebSearchMaxSnippetChars(22);
        WebSearchClient client = new WebSearchClient(properties);

        var outcome = client.search("AI news", 3);

        assertThat(outcome.status()).isEqualTo(AgentSearchStatus.OK);
        assertThat(outcome.domain()).isEqualTo("web");
        assertThat(outcome.provider()).isEqualTo("tavily-compatible");
        assertThat(outcome.durationMs()).isNotNull();
        assertThat(outcome.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("WEB_SEARCH");
            assertThat(item.sourceLabel()).isEqualTo("网页");
            assertThat(item.sourceId()).isEqualTo("https://example.com/ai");
            assertThat(item.courseId()).isNull();
            assertThat(item.title()).isEqualTo("AI News");
            assertThat(item.contextLabel()).isEqualTo("https://example.com/ai");
            assertThat(item.snippet()).isEqualTo("This is a long AI news");
            assertThat(item.relationLabel()).isEqualTo("联网搜索");
            assertThat(item.metadata())
                    .containsEntry("url", "https://example.com/ai")
                    .containsEntry("provider", "tavily-compatible")
                    .containsEntry("score", 0.92)
                    .containsEntry("publishedDate", "2026-06-24");
            assertThat(item.indexInfo()).containsEntry("url", "https://example.com/ai");
        });
    }

    @Test
    void searchShouldReturnEmptyWhenResponseHasNoValidUrls() throws Exception {
        startServer("""
                {
                  "results": [
                    {
                      "title": "No URL",
                      "content": "Missing URL"
                    }
                  ]
                }
                """);
        WebSearchClient client = new WebSearchClient(webProperties());

        var outcome = client.search("AI news", 3);

        assertThat(outcome.status()).isEqualTo(AgentSearchStatus.EMPTY);
        assertThat(outcome.items()).isEmpty();
        assertThat(outcome.retryable()).isTrue();
    }

    @Test
    void searchShouldReturnFailedWhenHttpRequestFails() {
        AiProperties properties = new AiProperties();
        properties.getAgentSearch().setWebSearchEnabled(true);
        properties.getAgentSearch().setWebSearchApiKey("test-key");
        properties.getAgentSearch().setWebSearchEndpoint("http://127.0.0.1:1/search");
        WebSearchClient client = new WebSearchClient(properties);

        var outcome = client.search("AI news", 3);

        assertThat(outcome.status()).isEqualTo(AgentSearchStatus.FAILED);
        assertThat(outcome.items()).isEmpty();
        assertThat(outcome.reason()).contains("不可达");
        assertThat(outcome.retryable()).isTrue();
    }

    @Test
    void searchShouldReturnFailedWhenProviderRejectsApiKey() throws Exception {
        startServer("{}", 401);
        WebSearchClient client = new WebSearchClient(webProperties());

        var outcome = client.search("AI news", 3);

        assertThat(outcome.status()).isEqualTo(AgentSearchStatus.FAILED);
        assertThat(outcome.reason()).contains("API Key");
        assertThat(outcome.retryable()).isFalse();
    }

    private AiProperties webProperties() {
        AiProperties properties = new AiProperties();
        properties.getAgentSearch().setWebSearchEnabled(true);
        properties.getAgentSearch().setWebSearchApiKey("test-key");
        properties.getAgentSearch().setWebSearchEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/search");
        properties.getAgentSearch().setWebSearchTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private void startServer(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            if (!"Bearer test-key".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                write(exchange, 401, "{}");
                return;
            }
            ObjectMapper mapper = new ObjectMapper();
            var request = mapper.readTree(exchange.getRequestBody());
            if (!"AI news".equals(request.path("query").asText())
                    || request.path("max_results").asInt() != 3
                    || !"basic".equals(request.path("search_depth").asText())
                    || request.path("include_answer").asBoolean(true)
                    || request.path("include_raw_content").asBoolean(true)) {
                write(exchange, 400, "{}");
                return;
            }
            write(exchange, 200, responseBody);
        });
        server.start();
    }

    private void startServer(String responseBody, int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> write(exchange, status, responseBody));
        server.start();
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
