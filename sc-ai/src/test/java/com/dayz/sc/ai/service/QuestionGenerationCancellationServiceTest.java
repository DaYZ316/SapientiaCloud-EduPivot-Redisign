package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class QuestionGenerationCancellationServiceTest {

    @Test
    void cancelShouldWriteRedisMarkerWithConfiguredTtl() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AiProperties properties = new AiProperties();
        properties.getGeneration().setCancelTtl(Duration.ofMinutes(30));
        QuestionGenerationCancellationService service = new QuestionGenerationCancellationService(redisTemplate, properties);

        service.cancel("request-1");

        verify(valueOperations).set("generation:cancelled:request-1", "1", Duration.ofMinutes(30));
    }

    @Test
    void throwIfCancelledShouldUseRedisMarker() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.hasKey("generation:cancelled:request-1")).thenReturn(true);
        QuestionGenerationCancellationService service = new QuestionGenerationCancellationService(redisTemplate, new AiProperties());

        assertThat(service.isCancelled("request-1")).isTrue();
        assertThatThrownBy(() -> service.throwIfCancelled("request-1"))
                .isInstanceOf(GenerationCancelledException.class);
    }
}
