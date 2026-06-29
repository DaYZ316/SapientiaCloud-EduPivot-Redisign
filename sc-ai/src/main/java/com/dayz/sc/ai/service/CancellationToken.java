package com.dayz.sc.ai.service;

/**
 * Lightweight cancellation hook for long-running generation flows.
 */
@FunctionalInterface
public interface CancellationToken {

    void throwIfCancelled();

    static CancellationToken none() {
        return () -> {
        };
    }
}
