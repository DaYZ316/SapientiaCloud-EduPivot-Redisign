package com.dayz.sc.common.redis.config;

import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

/**
 * 为公共模块配置 Redis 缓存和 JSON 序列化模板
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@AutoConfiguration
@EnableCaching
public class RedisAutoConfiguration {
    private static RedisTemplate<String, Object> createRedisTemplate(
            RedisConnectionFactory connectionFactory,
            StringRedisSerializer stringSerializer,
            GenericJacksonJsonRedisSerializer jsonSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    private static GenericJacksonJsonRedisSerializer jsonRedisSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(redisTypeValidator())
                .customize(builder -> builder
                        .findAndAddModules()
                        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS))
                .build();
    }

    private static PolymorphicTypeValidator redisTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType((context, subType) -> true)
                .build();
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJacksonJsonRedisSerializer jsonSerializer = jsonRedisSerializer();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "courseDetail", defaultConfig.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .transactionAware()
                .build();
    }

    @Bean
    public KafkaIdempotencyGuard kafkaIdempotencyGuard(StringRedisTemplate redisTemplate) {
        return new KafkaIdempotencyGuard(redisTemplate);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        GenericJacksonJsonRedisSerializer jsonSerializer = jsonRedisSerializer();
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        return createRedisTemplate(connectionFactory, stringSerializer, jsonSerializer);
    }
}
