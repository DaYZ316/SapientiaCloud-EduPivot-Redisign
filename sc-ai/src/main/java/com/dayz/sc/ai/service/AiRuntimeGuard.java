package com.dayz.sc.ai.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiRuntimeGuard {

    private static final String DUMMY_KEY = "missing-dashscope-key";

    private final String apiKey;

    public AiRuntimeGuard(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !DUMMY_KEY.equals(apiKey);
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, missingKeyMessage());
        }
    }

    public String missingKeyMessage() {
        return "AI 服务暂时不可用，请稍后再试或联系管理员。";
    }
}
