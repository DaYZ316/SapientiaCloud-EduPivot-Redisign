package com.dayz.sc.notification.config;

import com.dayz.sc.notification.sse.RedisSseSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class SseRedisPubSubConfiguration {

    @Bean
    public RedisMessageListenerContainer sseRedisListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisSseSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic("sc:notification:sse"));
        return container;
    }
}
