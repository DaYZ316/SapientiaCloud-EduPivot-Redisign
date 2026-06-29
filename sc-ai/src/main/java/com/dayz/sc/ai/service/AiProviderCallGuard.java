package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * AiProviderCallGuard.
 *
 * @author DaYZ
 */
@Component
public class AiProviderCallGuard {

    private final AiProperties aiProperties;
    private final Semaphore semaphore;

    public AiProviderCallGuard() {
        this(new AiProperties());
    }

    public AiProviderCallGuard(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
        this.semaphore = new Semaphore(Math.max(1, aiProperties.getGeneration().getProviderConcurrency()));
    }

    public <T> T call(Supplier<T> supplier) {
        boolean acquired = acquire();
        try {
            return supplier.get();
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    public void run(Runnable runnable) {
        call(() -> {
            runnable.run();
            return null;
        });
    }

    private boolean acquire() {
        try {
            boolean acquired = semaphore.tryAcquire(
                    aiProperties.getGeneration().getProviderAcquireTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new BusinessException(ErrorCodes.SERVICE_UNAVAILABLE);
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GenerationCancelledException("provider-call");
        }
    }
}
