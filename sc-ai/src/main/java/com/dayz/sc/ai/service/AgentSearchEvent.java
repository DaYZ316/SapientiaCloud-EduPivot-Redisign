package com.dayz.sc.ai.service;

import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.util.UuidV7Generator;

import java.util.List;

public record AgentSearchEvent(
        String searchId,
        String phase,
        String domain,
        String label,
        String query,
        String occurredAt,
        int total,
        List<AgentSearchItem> items
) {
    public AgentSearchEvent(String phase,
                            String domain,
                            String label,
                            String query,
                            int total,
                            List<AgentSearchItem> items) {
        this(newSearchId(), phase, domain, label, query, now(), total, items == null ? List.of() : items);
    }

    public static AgentSearchEvent started(String domain, String label, String query) {
        return started(newSearchId(), domain, label, query);
    }

    public static AgentSearchEvent started(String searchId, String domain, String label, String query) {
        return new AgentSearchEvent(searchId, "started", domain, label, query, now(), 0, List.of());
    }

    public static AgentSearchEvent results(String domain, String label, String query, List<AgentSearchItem> items) {
        return results(newSearchId(), domain, label, query, items);
    }

    public static AgentSearchEvent results(String searchId,
                                           String domain,
                                           String label,
                                           String query,
                                           List<AgentSearchItem> items) {
        List<AgentSearchItem> safeItems = items == null ? List.of() : items;
        return new AgentSearchEvent(
                searchId,
                safeItems.isEmpty() ? "empty" : "results",
                domain,
                label,
                query,
                now(),
                safeItems.size(),
                safeItems);
    }

    public static AgentSearchEvent error(String domain, String label, String query) {
        return error(newSearchId(), domain, label, query);
    }

    public static AgentSearchEvent error(String searchId, String domain, String label, String query) {
        return new AgentSearchEvent(searchId, "error", domain, label, query, now(), 0, List.of());
    }

    public static AgentSearchEvent completed() {
        return new AgentSearchEvent(newSearchId(), "completed", "agentSearch", "检索完成", "", now(), 0, List.of());
    }

    private static String newSearchId() {
        return UuidV7Generator.generate().toString();
    }

    private static String now() {
        return java.time.Instant.now().toString();
    }
}
