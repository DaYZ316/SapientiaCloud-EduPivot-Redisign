package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAgentService {

    private final ChatClient chatClient;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final PlatformDataTool platformDataTool;
    private final QuestionGenerationService questionGenerationService;

    public AiAgentResult run(ChatRequest request) {
        AiAgentMode mode = AiAgentMode.resolve(request.agentMode(), request.message());
        AiCourseContext context = platformDataTool.loadCourseContext(request.courseId());
        return switch (mode) {
            case QUESTION -> questionGenerationService.generateQuestions(request.message(), request.generation(), context);
            case PAPER -> questionGenerationService.generatePaper(request.message(), request.generation(), context);
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
        String prompt = """
                You are the SapientiaCloud EduPivot teaching assistant.
                Answer using only the current user's authorized platform context and general teaching reasoning.
                If context is insufficient, say so.

                User question:
                %s

                Authorized platform context:
                %s
                """.formatted(request.message(), platformDataTool.summarize(context));
        String content = chatClient.prompt().user(prompt).call().content();
        return new AiAgentResult(content, AiMessageType.TEXT, Map.of(
                "courseCount", context.courses().size(),
                "chapterCount", context.chapters().size(),
                "questionCount", context.questions().size()
        ));
    }
}
