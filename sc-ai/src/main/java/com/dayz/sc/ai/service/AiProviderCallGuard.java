package com.dayz.sc.ai.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * AiProviderCallGuard.
 *
 * @author DaYZ
 */
@Component
public class AiProviderCallGuard {

    private final ReentrantLock lock = new ReentrantLock();

    public <T> T call(Supplier<T> supplier) {
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    public void run(Runnable runnable) {
        call(() -> {
            runnable.run();
            return null;
        });
    }
}
