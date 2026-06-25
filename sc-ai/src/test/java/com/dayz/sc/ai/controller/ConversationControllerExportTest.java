package com.dayz.sc.ai.controller;

import com.dayz.sc.ai.service.AiGenerationExportService;
import com.dayz.sc.ai.service.ConversationService;
import com.dayz.sc.ai.service.RagChatService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationControllerExportTest {

    @Test
    void exportMessageShouldReturnBinaryAttachment() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        byte[] bytes = "%PDF".getBytes(StandardCharsets.US_ASCII);
        AiGenerationExportService exportService = mock(AiGenerationExportService.class);
        when(exportService.export(conversationId, messageId, userId, "pdf", true))
                .thenReturn(new AiGenerationExportService.ExportFile("paper.pdf", "application/pdf", bytes));
        MockMvc mockMvc = mockMvc(userId, exportService);

        mockMvc.perform(get("/api/ai/conversations/{id}/messages/{messageId}/export", conversationId, messageId)
                        .param("format", "pdf")
                        .param("includeAnswers", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("paper.pdf")))
                .andExpect(content().bytes(bytes));

        verify(exportService).export(conversationId, messageId, userId, "pdf", true);
    }

    @Test
    void exportMessageShouldDefaultIncludeAnswersToFalse() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        AiGenerationExportService exportService = mock(AiGenerationExportService.class);
        when(exportService.export(conversationId, messageId, userId, "docx", false))
                .thenReturn(new AiGenerationExportService.ExportFile(
                        "paper.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        new byte[]{1, 2, 3}));
        MockMvc mockMvc = mockMvc(userId, exportService);

        mockMvc.perform(get("/api/ai/conversations/{id}/messages/{messageId}/export", conversationId, messageId)
                        .param("format", "docx"))
                .andExpect(status().isOk());

        verify(exportService).export(conversationId, messageId, userId, "docx", false);
    }

    private static MockMvc mockMvc(UUID userId, AiGenerationExportService exportService) {
        ConversationController controller = new ConversationController(
                mock(ConversationService.class),
                exportService,
                mock(RagChatService.class));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new JwtArgumentResolver(jwt))
                .build();
    }

    private record JwtArgumentResolver(Jwt jwt) implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) {
            return jwt;
        }
    }
}
