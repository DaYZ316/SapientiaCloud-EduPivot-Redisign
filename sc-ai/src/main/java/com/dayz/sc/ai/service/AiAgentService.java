package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final ChatClient chatClient;
    private final AiProperties aiProperties;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final PlatformDataTool platformDataTool;
    private final QuestionGenerationService questionGenerationService;
    private final AiProviderCallGuard aiProviderCallGuard;

    public AiAgentResult run(ChatRequest request) {
        AiAgentMode mode = AiAgentMode.resolve(request.agentMode(), request.message());
        AiCourseContext context = platformDataTool.loadCourseContext(request.courseId());
        return switch (mode) {
            case QUESTION -> questionGenerationService.generateQuestions(request.message(), request.generation(), context);
            case PAPER -> questionGenerationService.generatePaper(request.message(), request.generation(), context);
            case CHAT -> chat(request, context);
        };
    }

    public AiAgentResult runGeneration(ChatRequest request,
                                       UUID userId,
                                       Integer role,
                                       Consumer<GenerationStageEvent> stageListener,
                                       Consumer<AgentSearchEvent> agentSearchListener) {
        return runGeneration(request, userId, role, stageListener, agentSearchListener, null);
    }

    public AiAgentResult runGeneration(ChatRequest request,
                                       UUID userId,
                                       Integer role,
                                       Consumer<GenerationStageEvent> stageListener,
                                       Consumer<AgentSearchEvent> agentSearchListener,
                                       String requestId) {
        AiAgentMode mode = AiAgentMode.resolve(request.agentMode(), request.message());
        AiCourseContext context = platformDataTool.loadCourseContext(request.courseId());
        return switch (mode) {
            case QUESTION -> questionGenerationService.generateQuestions(
                    request.message(),
                    request.generation(),
                    context,
                    userId,
                    role,
                    request.courseId(),
                    stageListener,
                    agentSearchListener,
                    requestId);
            case PAPER -> questionGenerationService.generatePaper(
                    request.message(),
                    request.generation(),
                    context,
                    userId,
                    role,
                    request.courseId(),
                    stageListener,
                    agentSearchListener,
                    requestId);
            case CHAT -> chat(request, context);
        };
    }

    private AiAgentResult chat(ChatRequest request, AiCourseContext context) {
        if (!aiRuntimeGuard.isConfigured()) {
            return new AiAgentResult(aiRuntimeGuard.missingKeyMessage(), AiMessageType.TEXT, Map.of(
                    "status", "AI_NOT_CONFIGURED",
                    "message", aiRuntimeGuard.missingKeyMessage()
            ));
        }
        String systemPrompt = aiProperties.getChat().getCourseSystemPrompt()
                .replace("{context}", platformDataTool.summarize(context));
        String content = aiProviderCallGuard.call(() -> chatClient.prompt()
                .system(systemPrompt)
                .user(request.message())
                .call()
                .content());
        return new AiAgentResult(content, AiMessageType.TEXT, Map.of(
                "courseCount", context.courses().size(),
                "chapterCount", context.chapters().size(),
                "questionCount", context.questions().size()
        ));
    }
}
