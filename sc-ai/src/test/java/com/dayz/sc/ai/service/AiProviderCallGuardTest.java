package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderCallGuardTest {

    @Test
    void callShouldFailFastWhenConcurrencyPermitCannotBeAcquired() throws Exception {
        AiProperties properties = new AiProperties();
        properties.getGeneration().setProviderConcurrency(1);
        properties.getGeneration().setProviderAcquireTimeout(Duration.ofMillis(50));
        AiProviderCallGuard guard = new AiProviderCallGuard(properties);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> guard.run(() -> {
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> guard.call(() -> "blocked"))
                .isInstanceOf(BusinessException.class);

        release.countDown();
        holder.join(TimeUnit.SECONDS.toMillis(1));
    }
}
