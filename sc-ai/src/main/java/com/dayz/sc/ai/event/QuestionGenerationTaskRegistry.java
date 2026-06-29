package com.dayz.sc.ai.event;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Component
public class QuestionGenerationTaskRegistry {

    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public void register(String requestId, Future<?> future) {
        if (StringUtils.hasText(requestId) && future != null) {
            runningTasks.put(requestId, future);
        }
    }

    public boolean cancel(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return false;
        }
        Future<?> future = runningTasks.remove(requestId);
        return future != null && future.cancel(true);
    }

    public void remove(String requestId) {
        if (StringUtils.hasText(requestId)) {
            runningTasks.remove(requestId);
        }
    }
}
