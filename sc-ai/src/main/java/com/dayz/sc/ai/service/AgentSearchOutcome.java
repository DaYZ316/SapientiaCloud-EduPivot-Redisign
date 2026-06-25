package com.dayz.sc.ai.service;

import com.dayz.sc.common.feign.dto.AgentSearchItem;

import java.util.List;

public record AgentSearchOutcome(
        AgentSearchStatus status,
        String domain,
        String provider,
        String query,
        String label,
        String reason,
        boolean retryable,
        Long durationMs,
        List<AgentSearchItem> items
) {
    public AgentSearchOutcome {
        status = status == null ? AgentSearchStatus.EMPTY : status;
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static AgentSearchOutcome ok(String domain,
                                        String provider,
                                        String query,
                                        String label,
                                        Long durationMs,
                                        List<AgentSearchItem> items) {
        List<AgentSearchItem> safeItems = items == null ? List.of() : items;
        AgentSearchStatus status = safeItems.isEmpty() ? AgentSearchStatus.EMPTY : AgentSearchStatus.OK;
        return new AgentSearchOutcome(status, domain, provider, query, label, null, false, durationMs, safeItems);
    }

    public static AgentSearchOutcome empty(String domain, String provider, String query, String label, Long durationMs) {
        return new AgentSearchOutcome(AgentSearchStatus.EMPTY, domain, provider, query, label, null, true, durationMs, List.of());
    }

    public static AgentSearchOutcome disabled(String domain, String provider, String query, String reason) {
        return new AgentSearchOutcome(AgentSearchStatus.DISABLED, domain, provider, query, "联网搜索未启用", reason, false, null, List.of());
    }

    public static AgentSearchOutcome misconfigured(String domain, String provider, String query, String reason) {
        return new AgentSearchOutcome(AgentSearchStatus.MISCONFIGURED, domain, provider, query, "联网搜索未配置", reason, false, null, List.of());
    }

    public static AgentSearchOutcome failed(String domain,
                                            String provider,
                                            String query,
                                            String label,
                                            String reason,
                                            boolean retryable,
                                            Long durationMs) {
        return new AgentSearchOutcome(AgentSearchStatus.FAILED, domain, provider, query, label, reason, retryable, durationMs, List.of());
    }

    public boolean hasResults() {
        return status == AgentSearchStatus.OK && !items.isEmpty();
    }
}
