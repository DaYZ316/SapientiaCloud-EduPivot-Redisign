package com.dayz.sc.common.events.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 配置类
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableKafka
public class KafkaErrorHandlerAutoConfiguration {

    /**
     * 全局 Kafka 消费错误处理：重试 3 次（间隔 1 秒），失败后发送到死信 topic
     * Spring Boot 自动将此 Bean 注入到 ConcurrentKafkaListenerContainerFactory
     */
    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<@NonNull String, @NonNull Object> dltKafkaTemplate) {
        return new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(dltKafkaTemplate),
                new FixedBackOff(1_000L, 3L));
    }

    @Bean
    public NewTopic courseEventsTopic() {
        return topic(KafkaTopicConstants.COURSE_EVENTS);
    }

    @Bean
    public NewTopic userEventsTopic() {
        return topic(KafkaTopicConstants.USER_EVENTS);
    }

    @Bean
    public NewTopic courseEventsDltTopic() {
        return topic(KafkaTopicConstants.COURSE_EVENTS + KafkaTopicConstants.DLT_SUFFIX);
    }

    @Bean
    public NewTopic userEventsDltTopic() {
        return topic(KafkaTopicConstants.USER_EVENTS + KafkaTopicConstants.DLT_SUFFIX);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
