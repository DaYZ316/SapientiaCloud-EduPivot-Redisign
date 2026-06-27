package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSearchClient.
 *
 * @author DaYZ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchClient {

    private static final int DEFAULT_MAX_RESULTS = 5;
    private static final int HARD_MAX_RESULTS = 10;
    private static final String PROVIDER = "tavily-compatible";
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final AiProperties aiProperties;

    public AgentSearchOutcome search(String query, Integer limit) {
        AiProperties.AgentSearch properties = aiProperties.getAgentSearch();
        String normalizedQuery = StringUtils.hasText(query) ? query.strip() : "";
        if (!properties.isWebSearchEnabled()) {
            return AgentSearchOutcome.disabled("web", PROVIDER, normalizedQuery, "AGENT_SEARCH_WEB_SEARCH_ENABLED=false");
        }
        if (!StringUtils.hasText(properties.getWebSearchEndpoint())
                || !StringUtils.hasText(properties.getWebSearchApiKey())) {
            return AgentSearchOutcome.misconfigured("web", PROVIDER, normalizedQuery, "缺少联网搜索 endpoint 或 api key");
        }
        if (!StringUtils.hasText(normalizedQuery)) {
            return AgentSearchOutcome.empty("web", PROVIDER, normalizedQuery, "没有可搜索的关键词", null);
        }

        int resultLimit = normalizeLimit(limit, properties.getWebSearchMaxResults());
        long startedAtNanos = System.nanoTime();
        try {
            WebSearchResponse response = client(properties)
                    .post()
                    .uri(properties.getWebSearchEndpoint().strip())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getWebSearchApiKey().strip())
                    .body(Map.of(
                            "query", query.strip(),
                            "max_results", resultLimit,
                            "search_depth", "basic",
                            "include_answer", false,
                            "include_raw_content", false))
                    .retrieve()
                    .body(WebSearchResponse.class);
            List<AgentSearchItem> items = toItems(response, properties.getWebSearchMaxSnippetChars());
            Long durationMs = durationMs(startedAtNanos);
            if (items.isEmpty()) {
                return AgentSearchOutcome.empty("web", PROVIDER, normalizedQuery, "未找到可引用网页结果", durationMs);
            }
            return AgentSearchOutcome.ok(
                    "web",
                    PROVIDER,
                    normalizedQuery,
                    "找到 " + items.size() + " 条网页结果",
                    durationMs,
                    items);
        } catch (RestClientResponseException e) {
            log.warn("AgentSearch web search failed query={} status={}", normalizedQuery, e.getStatusCode(), e);
            return AgentSearchOutcome.failed(
                    "web",
                    PROVIDER,
                    normalizedQuery,
                    "联网搜索失败",
                    httpFailureReason(e.getStatusCode()),
                    e.getStatusCode().is5xxServerError() || e.getStatusCode().value() == HTTP_TOO_MANY_REQUESTS,
                    durationMs(startedAtNanos));
        } catch (ResourceAccessException e) {
            log.warn("AgentSearch web search failed query={} reason=resource_access", normalizedQuery, e);
            return AgentSearchOutcome.failed(
                    "web",
                    PROVIDER,
                    normalizedQuery,
                    "联网搜索超时或不可达",
                    "联网搜索服务暂时不可达",
                    true,
                    durationMs(startedAtNanos));
        } catch (RuntimeException e) {
            log.warn("AgentSearch web search failed query={}", normalizedQuery, e);
            return AgentSearchOutcome.failed(
                    "web",
                    PROVIDER,
                    normalizedQuery,
                    "联网搜索失败",
                    "联网搜索响应解析失败或服务异常",
                    true,
                    durationMs(startedAtNanos));
        }
    }

    private RestClient client(AiProperties.AgentSearch properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(properties.getWebSearchTimeout()))
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        factory.setConnectTimeout(effectiveTimeout);
        factory.setReadTimeout(effectiveTimeout);
        return factory;
    }

    private List<AgentSearchItem> toItems(WebSearchResponse response, int snippetLimit) {
        if (response == null || response.results() == null || response.results().isEmpty()) {
            return List.of();
        }
        List<AgentSearchItem> items = new ArrayList<>();
        for (WebSearchResult result : response.results()) {
            if (result == null || !StringUtils.hasText(result.url())) {
                continue;
            }
            items.add(toItem(result, response, snippetLimit));
        }
        return items;
    }

    private AgentSearchItem toItem(WebSearchResult result, WebSearchResponse response, int snippetLimit) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("url", result.url());
        metadata.put("provider", PROVIDER);
        if (result.score() != null) {
            metadata.put("score", result.score());
        }
        if (StringUtils.hasText(result.publishedDate())) {
            metadata.put("publishedDate", result.publishedDate());
        }
        if (StringUtils.hasText(result.favicon())) {
            metadata.put("favicon", result.favicon());
        }
        if (response != null && StringUtils.hasText(response.requestId())) {
            metadata.put("requestId", response.requestId());
        }
        if (response != null && response.responseTime() != null) {
            metadata.put("responseTime", response.responseTime());
        }

        return new AgentSearchItem(
                "WEB_SEARCH",
                "网页",
                result.url(),
                null,
                StringUtils.hasText(result.title()) ? result.title().strip() : result.url(),
                result.url(),
                snippet(result.content(), snippetLimit),
                "联网搜索",
                metadata,
                Map.copyOf(metadata));
    }

    private int normalizeLimit(Integer requestedLimit, int configuredLimit) {
        int fallbackLimit = configuredLimit > 0 ? configuredLimit : DEFAULT_MAX_RESULTS;
        int limit = requestedLimit == null || requestedLimit <= 0 ? fallbackLimit : requestedLimit;
        return Math.min(limit, HARD_MAX_RESULTS);
    }

    private String snippet(String value, int limit) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        int maxLength = limit > 0 ? limit : 500;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private Long durationMs(long startedAtNanos) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
    }

    private String httpFailureReason(HttpStatusCode statusCode) {
        int value = statusCode.value();
        if (value == HTTP_UNAUTHORIZED || value == HTTP_FORBIDDEN) {
            return "联网搜索认证失败，请检查 API Key";
        }
        if (value == HTTP_TOO_MANY_REQUESTS) {
            return "联网搜索请求过于频繁或额度不足";
        }
        if (statusCode.is5xxServerError()) {
            return "联网搜索服务暂时异常";
        }
        return "联网搜索请求失败，HTTP " + value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebSearchResponse(List<WebSearchResult> results,
                             @JsonProperty("request_id") String requestId,
                             @JsonProperty("response_time") Double responseTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WebSearchResult(String title,
                           String url,
                           String content,
                           Double score,
                           @JsonProperty("published_date") String publishedDate,
                           String favicon,
                           @JsonProperty("raw_content") JsonNode rawContent) {
    }
}
