package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlatformApiSearchClient.
 *
 * @author DaYZ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformApiSearchClient {

    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^}/]+)}");
    private static final Set<String> BLOCKED_PATH_PARTS = Set.of("/internal/", "/openapi/", "/scalar", "/swagger-ui");
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final String HTTP_METHOD_GET = "get";
    private static final String PATH_SEPARATOR = "/";

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    private final Map<String, Map<String, OpenApiOperation>> operationCache = new ConcurrentHashMap<>();
    private final Map<String, String> operationLoadErrors = new ConcurrentHashMap<>();

    public AgentSearchItem query(String service,
                                 String path,
                                 Map<String, Object> queryParams,
                                 String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return errorItem("未携带登录凭证，无法读取平台资料。");
        }
        String normalizedService = normalizeService(service);
        String normalizedPath = normalizePath(path);
        if (!StringUtils.hasText(normalizedService) || !StringUtils.hasText(normalizedPath)) {
            return errorItem("平台查询需要指定服务和路径。");
        }
        if (isBlockedPath(normalizedPath)) {
            return errorItem("该路径不允许通过 AgentSearch 查询。");
        }
        if (pathVariableCount(normalizedPath) > 0) {
            return errorItem("平台查询路径需要使用实际参数，不能包含模板变量。");
        }

        OpenApiOperation operation = getOperation(normalizedService, normalizedPath);
        if (operation == null) {
            String loadError = operationLoadErrors.get(normalizedService);
            if (StringUtils.hasText(loadError)) {
                return errorItem("平台 OpenAPI 暂时无法读取，无法确认该接口是否可查询。");
            }
            return errorItem("该只读接口未在平台 OpenAPI 中登记，无法查询。");
        }

        try {
            String body = get(normalizedPath, queryParams, authorization);
            return new AgentSearchItem(
                    "PLATFORM_API",
                    "平台接口",
                    null,
                    null,
                    operation.title(),
                    normalizedService,
                    responseSnippet(body),
                    "已按当前用户权限查询",
                    Map.of("service", normalizedService, "path", normalizedPath),
                    Map.of(
                            "service", normalizedService,
                            "path", normalizedPath,
                            "method", "GET",
                            "queryParams", queryParams == null ? Map.of() : queryParams));
        } catch (PlatformApiAccessException e) {
            return errorItem(e.getMessage());
        } catch (RuntimeException e) {
            log.warn("AgentSearch platform API query failed service={} path={}", normalizedService, normalizedPath, e);
            return errorItem("平台资料暂时无法读取。");
        }
    }

    private String get(String path, Map<String, Object> queryParams, String authorization) {
        URI uri = buildUri(path, queryParams);
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl())
                .requestFactory(requestFactory())
                .build();
        String response = client.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, responseBody) -> {
                    if (responseBody.getStatusCode().value() == HTTP_UNAUTHORIZED) {
                        throw new PlatformApiAccessException("登录凭证已失效，无法读取该资料。");
                    }
                    if (responseBody.getStatusCode().value() == HTTP_FORBIDDEN) {
                        throw new PlatformApiAccessException("你没有权限查看该资料。");
                    }
                    throw new PlatformApiAccessException("该平台资料无法读取。");
                })
                .body(String.class);
        return response == null ? "" : response;
    }

    private URI buildUri(String path, Map<String, Object> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        Map<String, Object> params = queryParams == null ? Map.of() : queryParams;
        params.forEach((key, value) -> {
            if (StringUtils.hasText(key) && value != null) {
                builder.queryParam(key, value);
            }
        });
        return builder.build().encode().toUri();
    }

    private OpenApiOperation getOperation(String service, String path) {
        Map<String, OpenApiOperation> operations = operationCache.get(service);
        if (operations == null || operations.isEmpty()) {
            operations = loadOperations(service);
            operationCache.put(service, operations);
        }
        OpenApiOperation exact = operations.get(path);
        if (exact != null) {
            return exact;
        }
        return operations.values().stream()
                .filter(operation -> operation.matches(path))
                .findFirst()
                .orElse(null);
    }

    private Map<String, OpenApiOperation> loadOperations(String service) {
        if (!aiProperties.getAgentSearch().getOpenApiSources().contains(service)) {
            operationLoadErrors.put(service, "unsupported-source");
            return Map.of();
        }
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl())
                    .requestFactory(requestFactory())
                    .build();
            String document = client.get()
                    .uri("/openapi/" + service)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            Map<String, OpenApiOperation> operations = parseOperations(document);
            if (operations.isEmpty()) {
                operationLoadErrors.put(service, "empty-openapi");
                log.warn("AgentSearch OpenAPI load returned no GET operations service={} baseUrl={}",
                        service, baseUrl());
            } else {
                operationLoadErrors.remove(service);
                log.info("AgentSearch OpenAPI loaded service={} operationCount={}", service, operations.size());
            }
            return operations;
        } catch (RuntimeException e) {
            operationLoadErrors.put(service, e.getClass().getSimpleName());
            log.warn("AgentSearch OpenAPI load failed service={}", service, e);
            return Map.of();
        }
    }

    private Map<String, OpenApiOperation> parseOperations(String document) {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            JsonNode paths = objectMapper.readTree(document).path("paths");
            if (!paths.isObject()) {
                return Map.of();
            }
            Map<String, OpenApiOperation> operations = new LinkedHashMap<>();
            paths.properties().forEach(entry -> {
                String path = normalizePath(entry.getKey());
                if (!isBlockedPath(path) && entry.getValue().has(HTTP_METHOD_GET)) {
                    JsonNode getOperation = entry.getValue().path(HTTP_METHOD_GET);
                    operations.put(path, new OpenApiOperation(path, operationTitle(path, getOperation)));
                }
            });
            return operations;
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String operationTitle(String path, JsonNode operation) {
        List<String> candidates = List.of(
                textValue(operation.path("summary")),
                textValue(operation.path("operationId")),
                path);
        return candidates.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(path);
    }

    private String textValue(JsonNode node) {
        return node.isTextual() ? node.asText() : "";
    }

    private String normalizeService(String service) {
        return StringUtils.hasText(service) ? service.strip().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.strip();
        if (!normalized.startsWith(PATH_SEPARATOR)) {
            normalized = PATH_SEPARATOR + normalized;
        }
        return removePathQuery(normalized);
    }

    private String removePathQuery(String path) {
        int queryStart = path.indexOf('?');
        return queryStart >= 0 ? path.substring(0, queryStart) : path;
    }

    private boolean isBlockedPath(String path) {
        String lowered = path.toLowerCase(Locale.ROOT);
        return BLOCKED_PATH_PARTS.stream().anyMatch(lowered::contains);
    }

    private long pathVariableCount(String path) {
        Matcher matcher = PATH_VARIABLE_PATTERN.matcher(path);
        long count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private String responseSnippet(String body) {
        if (!StringUtils.hasText(body)) {
            return "接口返回为空。";
        }
        String compact = compactJson(body.strip());
        int limit = aiProperties.getAgentSearch().getMaxResponseChars();
        if (limit <= 0) {
            limit = 4000;
        }
        if (compact.length() > limit) {
            throw new PlatformApiAccessException("接口返回内容过长，无法通过 AgentSearch 展示。");
        }
        return compact;
    }

    private String compactJson(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return body.replaceAll("\\s+", " ");
        }
    }

    private AgentSearchItem errorItem(String message) {
        return new AgentSearchItem(
                "PLATFORM_API_ERROR",
                "平台接口",
                "平台资料查询",
                "",
                Objects.requireNonNullElse(message, "平台资料暂时无法读取。"),
                "",
                Map.of());
    }

    private String baseUrl() {
        String configured = aiProperties.getAgentSearch().getGatewayBaseUrl();
        return StringUtils.hasText(configured) ? configured.strip() : "http://localhost:39080";
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = aiProperties.getAgentSearch().getTimeout();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    record OpenApiOperation(String path, String title) {
        boolean matches(String concretePath) {
            if (path.equals(concretePath)) {
                return true;
            }
            String regex = PATH_VARIABLE_PATTERN.matcher(path).replaceAll("[^/]+");
            return concretePath.matches(regex);
        }
    }

    static class PlatformApiAccessException extends RuntimeException {
        PlatformApiAccessException(String message) {
            super(message);
        }
    }
}
