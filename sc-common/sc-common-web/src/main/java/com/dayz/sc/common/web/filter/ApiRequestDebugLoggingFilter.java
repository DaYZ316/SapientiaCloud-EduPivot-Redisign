package com.dayz.sc.common.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Locale;

@Slf4j
public class ApiRequestDebugLoggingFilter extends OncePerRequestFilter {

    private static final String API_PATH_PREFIX = "/api/";
    private static final String HEADER_USER_ID = "X-User-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !log.isDebugEnabled() || !request.getRequestURI().startsWith(API_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
            log.debug("API request method={} path={} query={} status={} durationMs={} userId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    safeQuery(request),
                    response.getStatus(),
                    durationMs,
                    request.getHeader(HEADER_USER_ID));
        }
    }

    private String safeQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "-";
        }
        try {
            return UriComponentsBuilder.fromUriString("?" + query)
                    .build()
                    .getQueryParams()
                    .entrySet()
                    .stream()
                    .map(entry -> entry.getKey() + "=" + (sensitive(entry.getKey()) ? "***" : String.join(",", entry.getValue())))
                    .reduce((left, right) -> left + "&" + right)
                    .orElse("-");
        } catch (IllegalArgumentException exception) {
            return "<unparsed>";
        }
    }

    private boolean sensitive(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return normalized.contains("token")
                || normalized.contains("authorization")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("key");
    }
}
