package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Stores cooperative cancellation markers for question and paper generation tasks.
 */
@Service
@RequiredArgsConstructor
public class QuestionGenerationCancellationService {

    private static final String KEY_PREFIX = "generation:cancelled:";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;

    public void cancel(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(requestId), "1", aiProperties.getGeneration().getCancelTtl());
    }

    public boolean isCancelled(String requestId) {
        return StringUtils.hasText(requestId)
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(requestId)));
    }

    public void throwIfCancelled(String requestId) {
        if (Thread.currentThread().isInterrupted() || isCancelled(requestId)) {
            throw new GenerationCancelledException(requestId);
        }
    }

    public CancellationToken token(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return CancellationToken.none();
        }
        return () -> throwIfCancelled(requestId);
    }

    private String key(String requestId) {
        return KEY_PREFIX + requestId;
    }
}
