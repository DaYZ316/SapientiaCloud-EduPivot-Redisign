package com.dayz.sc.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class QuestionGenerationWorkerConfig {

    @Bean
    public TaskExecutor questionGenerationTaskExecutor(AiProperties aiProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("question-generation-");
        executor.setCorePoolSize(Math.max(1, aiProperties.getGeneration().getWorkerCoreSize()));
        executor.setMaxPoolSize(Math.max(
                executor.getCorePoolSize(),
                aiProperties.getGeneration().getWorkerMaxSize()));
        executor.setQueueCapacity(Math.max(1, executor.getMaxPoolSize()));
        executor.initialize();
        return executor;
    }
}
