package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.dto.ChatRequest;
import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.enums.MessageRole;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.events.ai.QuestionGenerationProgressEvent;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.codec.ServerSentEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class RagChatServiceTest {

    @Test
    void defaultChatPromptsShouldIncludeLatexRules() {
        AiProperties properties = new AiProperties();

        assertLatexRules(properties.getChat().getGeneralSystemPrompt());
        assertLatexRules(properties.getChat().getCourseSystemPrompt());
        assertLatexRules(properties.getRag().getSystemPrompt());
    }

    @Test
    void retrieveContextNowShouldReturnGeneralFallbackWhenNothingMatches() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        RagChatService service = serviceWith(vectorStore, new AiProperties());

        RagChatService.RagContext context = service.retrieveContextNow(UUID.randomUUID(), "question");

        assertThat(context.strategy()).isEqualTo(RagChatService.ChatStrategy.GENERAL_FALLBACK);
        assertThat(context.matchedChunkCount()).isZero();
        assertThat(context.knowledgeMatchedCount()).isZero();
        assertThat(context.chatMemoryMatchedCount()).isZero();
    }

    @Test
    void retrieveContextNowShouldMergeKnowledgeAndChatMemoryMatches() {
        UUID conversationId = UUID.randomUUID();
        VectorStore vectorStore = vectorStoreWithSourceResults(
                List.of(new Document("knowledge A", Map.of(KnowledgeBaseService.META_DOC_ID, "doc-a"))),
                List.of(new Document("User: old\nAI: answer", Map.of(
                        ChatVectorMemoryService.META_CONVERSATION_ID, conversationId.toString()))));
        RagChatService service = serviceWith(vectorStore, new AiProperties());

        RagChatService.RagContext context = service.retrieveContextNow(UUID.randomUUID(), "question");

        assertThat(context.strategy()).isEqualTo(RagChatService.ChatStrategy.RAG_MATCH);
        assertThat(context.context()).contains("knowledge A", "User: old");
        assertThat(context.matchedChunkCount()).isEqualTo(2);
        assertThat(context.knowledgeMatchedCount()).isEqualTo(1);
        assertThat(context.chatMemoryMatchedCount()).isEqualTo(1);
        assertThat(context.matchedDocIds()).containsExactly("doc-a");
        assertThat(context.matchedConversationIds()).containsExactly(conversationId.toString());
        assertThat(context.matchedSourceTypes())
                .containsExactly(KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC,
                        ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN);
    }

    @Test
    void retrieveContextNowShouldFilterByUserIdAndSourceTypes() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        RagChatService service = serviceWith(vectorStore, new AiProperties());
        UUID userId = UUID.randomUUID();

        service.retrieveContextNow(userId, "question");

        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                filterContains(request, userId.toString())
                        && filterContains(request, KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC)));
        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                filterContains(request, userId.toString())
                        && filterContains(request, ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)));
    }

    @Test
    void retrieveChatMemoryNowShouldUseChatMemoryOnly() {
        UUID conversationId = UUID.randomUUID();
        VectorStore vectorStore = vectorStoreWithSourceResults(
                List.of(new Document("knowledge A", Map.of(KnowledgeBaseService.META_DOC_ID, "doc-a"))),
                List.of(new Document("User: old\nAI: answer", Map.of(
                        ChatVectorMemoryService.META_CONVERSATION_ID, conversationId.toString()))));
        RagChatService service = serviceWith(vectorStore, new AiProperties());

        RagChatService.RagContext context = service.retrieveChatMemoryNow(UUID.randomUUID(), "question");

        assertThat(context.strategy()).isEqualTo(RagChatService.ChatStrategy.COURSE_CONTEXT);
        assertThat(context.context()).contains("User: old").doesNotContain("knowledge A");
        assertThat(context.knowledgeMatchedCount()).isZero();
        assertThat(context.chatMemoryMatchedCount()).isEqualTo(1);
    }

    @Test
    void recentMemoryMessagesShouldKeepTextAndCompletedGenerationMessagesInAscendingOrder() {
        UUID conversationId = UUID.randomUUID();
        UUID currentMessageId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findRecentByConversationId(conversationId, 13)).thenReturn(List.of(
                message(currentMessageId, MessageRole.USER, AiMessageType.TEXT, "current"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.TEXT, "old answer 2"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "old question 2"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.PAPER, "paper payload",
                        Map.of("generationStatus", "completed")),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.QUESTION_SET, "failed generation",
                        Map.of("generationStatus", "failed")),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.PAPER, "",
                        Map.of("generationStatus", "processing")),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.QUESTION_SET, "question set payload",
                        Map.of("generationStatus", "completed")),
                message(UUID.randomUUID(), MessageRole.SYSTEM, AiMessageType.TEXT, "system"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "old question 1")
        ));
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties(), messageRepository);

        List<ChatMessage> messages = service.recentMemoryMessages(conversationId, currentMessageId);

        assertThat(messages)
                .extracting(ChatMessage::getContent)
                .containsExactly("old question 1", "question set payload", "paper payload",
                        "old question 2", "old answer 2");
    }

    @Test
    void modelMessagesShouldBuildSystemHistoryAndCurrentQuestion() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());

        List<Message> messages = service.modelMessages("SYSTEM", List.of(
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "my name is Zhang San"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.TEXT, "ok")
        ), "what is my name?");

        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1).getText()).isEqualTo("my name is Zhang San");
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(3)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3).getText()).isEqualTo("what is my name?");
        assertThat(messages.getFirst().getText())
                .contains("当前登录用户角色：未知。")
                .contains("getCurrentDateTime")
                .doesNotContain("当前日期：");
    }

    @Test
    void modelMessagesShouldIncludeReadableGenerationArtifactContext() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());
        String paperContent = "## AI \u51fa\u5377\u7ed3\u679c\n\n\u7b2c1\u9898: HashMap load factor";

        List<Message> messages = service.modelMessages("SYSTEM", List.of(
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "generate paper"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.PAPER, paperContent,
                        Map.of(
                                "generationStatus", "completed",
                                "generationTrace", List.of(Map.of("stage", "RESPONDED")),
                                "generationDebugTrace", List.of("debug")))
        ), "\u8be6\u7ec6\u89e3\u6790\u4e00\u4e0b\u7b2c\u4e00\u9898");

        assertThat(messages).hasSize(4);
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).getText())
                .contains("\u4e0a\u4e00\u4efd AI \u51fa\u5377\u7ed3\u679c", "\u7b2c1\u9898", "HashMap")
                .doesNotContain("generationTrace", "generationDebugTrace");
    }

    @Test
    void modelMessagesShouldIncludeReadableRoleContextFromJwtRole() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());

        List<Message> messages = service.modelMessages("SYSTEM", List.of(), "current", 2);

        assertThat(messages.getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(messages.getFirst().getText())
                .contains("当前登录用户角色：教师。")
                .doesNotContain("当前日期：")
                .doesNotContain("role=2")
                .doesNotContain("用户 ID");
    }

    @Test
    void modelMessagesShouldIncludeWebSearchInstruction() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());

        List<Message> messages = service.modelMessages("SYSTEM", List.of(), "查一下最新 AI 新闻", 2);

        assertThat(messages.getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(messages.getFirst().getText())
                .contains("Use searchWeb only when the user explicitly asks to search the web")
                .contains("Prefer platform and course search tools for")
                .contains("do not present web")
                .contains("getCurrentDateTime")
                .contains("SYSTEM_TIME")
                .contains("Do not assume a stale year or month")
                .contains("If searchWeb returns EMPTY")
                .contains("DISABLED, MISCONFIGURED, or FAILED")
                .contains("do not answer fresh/current facts from memory");
    }

    @Test
    void modelMessagesShouldIncludeAgentSearchPreflightContext() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());

        List<Message> messages = service.modelMessages(
                "SYSTEM",
                List.of(),
                "查一下最新 AI 新闻",
                2,
                List.of(systemTimeOutcome("2026-06-24")));

        assertThat(messages.getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(messages.getFirst().getText())
                .contains("AgentSearch preflight context")
                .contains("domain=time")
                .contains("status=OK")
                .contains("provider=server-clock")
                .contains("sourceType=SYSTEM_TIME")
                .contains("date=2026-06-24")
                .contains("zoneId=Asia/Shanghai");
    }

    @Test
    void retrievalQueryShouldAppendRecentUserMessagesOnly() {
        AiProperties properties = new AiProperties();
        properties.getRag().setHistoryQueryUserMessages(2);
        RagChatService service = serviceWith(mock(VectorStore.class), properties);

        String query = service.retrievalQuery("current", List.of(
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "first"),
                message(UUID.randomUUID(), MessageRole.ASSISTANT, AiMessageType.TEXT, "first answer"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "second"),
                message(UUID.randomUUID(), MessageRole.USER, AiMessageType.TEXT, "third")
        ));

        assertThat(query).isEqualTo("second\nthird\ncurrent");
    }

    @Test
    void contextEventShouldExposeMemoryAndRagMetadata() {
        RagChatService service = serviceWith(mock(VectorStore.class), new AiProperties());
        UUID conversationId = UUID.randomUUID();

        ServerSentEvent<String> event = service.contextEvent(
                conversationId,
                3,
                RagChatService.RagContext.match(
                        "ctx",
                        1,
                        1,
                        Set.of("doc-a"),
                        Set.of("conv-a"),
                        Set.of(KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC,
                                ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)));

        assertThat(event.event()).isEqualTo("context");
        assertThat(event.data())
                .contains(conversationId.toString())
                .contains("\"memoryMessageCount\":3")
                .contains("\"ragStrategy\":\"RAG_MATCH\"")
                .contains("\"matchedChunkCount\":2")
                .contains("\"knowledgeMatchedCount\":1")
                .contains("\"chatMemoryMatchedCount\":1")
                .contains("doc-a")
                .contains("conv-a")
                .contains(ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN);
    }

    @Test
    void streamShouldTouchConversationUpdatedAtAfterPersistingUserMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationService conversationService = mock(ConversationService.class);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        when(aiAgentService.run(any(ChatRequest.class)))
                .thenReturn(new AiAgentResult("generated", AiMessageType.QUESTION_SET, Map.<String, Object>of()));
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                aiAgentService);

        service.stream(new ChatRequest(conversationId, "generate questions", "QUESTION", null, null), userId, 2, null)
                .collectList()
                .block();

        verify(conversationService).touchUpdatedAt(conversationId, userId);
    }

    @Test
    void generationUserMessageShouldPersistRequestPayload() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionBankId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationService conversationService = mock(ConversationService.class);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        when(aiAgentService.run(any(ChatRequest.class)))
                .thenReturn(new AiAgentResult("generated", AiMessageType.QUESTION_SET, Map.<String, Object>of()));
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                aiAgentService);
        GenerationRequest generation = new GenerationRequest(
                questionBankId,
                5,
                0,
                2,
                BigDecimal.valueOf(4),
                null,
                null,
                null,
                null,
                "覆盖 HashMap 默认负载因子。",
                null,
                List.of("HashMap"),
                List.of("理解底层原理"));

        service.stream(new ChatRequest(conversationId, "generate questions", "QUESTION", null, generation), userId, 2, null)
                .collectList()
                .block();

        verify(messageRepository).save(argThat(message -> {
            if (!MessageRole.USER.name().equals(message.getRole()) || message.getPayload() == null) {
                return false;
            }
            Map<?, ?> generationRequest = (Map<?, ?>) message.getPayload().get("generationRequest");
            return "generate questions".equals(message.getContent())
                    && "QUESTION".equals(message.getPayload().get("generationMode"))
                    && generationRequest != null
                    && questionBankId.toString().equals(generationRequest.get("questionBankId"))
                    && Integer.valueOf(5).equals(generationRequest.get("questionCount"))
                    && "覆盖 HashMap 默认负载因子。".equals(generationRequest.get("requirement"));
        }));
    }

    @Test
    void generationShouldUseCreatedConversationWhenRequestStartsNewConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID questionBankId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.createConversation(
                org.mockito.ArgumentMatchers.anyString(),
                any())).thenReturn(conversationId);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        when(aiAgentService.runGeneration(
                any(ChatRequest.class),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new AiAgentResult("generated", AiMessageType.QUESTION_SET, Map.<String, Object>of()));
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                aiAgentService);
        GenerationRequest generation = new GenerationRequest(
                questionBankId,
                5,
                0,
                2,
                BigDecimal.valueOf(4),
                null,
                null,
                null,
                null,
                "generate from a new conversation",
                null,
                List.of("HashMap"),
                List.of("鐞嗚В搴曞眰鍘熺悊"));

        List<ServerSentEvent<String>> events = service.stream(
                        new ChatRequest(null, "generate questions", "QUESTION", null, generation),
                        userId,
                        2,
                        null)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event).contains("conversation", "chunk");
        verify(aiAgentService).runGeneration(
                argThat(request -> conversationId.equals(request.conversationId())
                        && "generate questions".equals(request.message())
                        && "QUESTION".equals(request.agentMode())
                        && generation.equals(request.generation())),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void newQuestionGenerationConversationShouldGenerateTitleWithRequestContext() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.createConversation(
                org.mockito.ArgumentMatchers.anyString(),
                any())).thenReturn(conversationId);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        when(aiAgentService.runGeneration(
                any(ChatRequest.class),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new AiAgentResult("generated", AiMessageType.QUESTION_SET, Map.<String, Object>of()));
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec titleSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec titleCallSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(titleSpec);
        when(titleSpec.call()).thenReturn(titleCallSpec);
        when(titleCallSpec.content()).thenReturn("HashMap 出题");
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                aiAgentService,
                chatClient,
                mock(AgentSearchTools.class));
        GenerationRequest generation = new GenerationRequest(
                null,
                5,
                0,
                2,
                BigDecimal.valueOf(4),
                null,
                null,
                null,
                null,
                "覆盖 HashMap 默认负载因子",
                null,
                List.of("HashMap"),
                List.of("理解底层原理"));

        List<ServerSentEvent<String>> events = service.stream(
                        new ChatRequest(null, "帮我出题", "QUESTION", null, generation),
                        userId,
                        2,
                        null)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("conversation", "generation_result", "chunk", "conversation");
        assertThat(events.getFirst().data()).contains("帮我出题");
        assertThat(events.getLast().data()).contains("HashMap 出题");
        verify(conversationService).updateConversationTitle(conversationId, "HashMap 出题", userId);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(titleSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("生成任务：出题")
                .contains("要求：覆盖 HashMap 默认负载因子")
                .contains("知识点：[HashMap]")
                .contains("能力目标：[理解底层原理]");
    }

    @Test
    void newPaperGenerationConversationShouldGenerateTitleWithRequestContext() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.createConversation(
                org.mockito.ArgumentMatchers.anyString(),
                any())).thenReturn(conversationId);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        when(aiAgentService.runGeneration(
                any(ChatRequest.class),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new AiAgentResult("paper content", AiMessageType.PAPER, Map.<String, Object>of()));
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec titleSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec titleCallSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(titleSpec);
        when(titleSpec.call()).thenReturn(titleCallSpec);
        when(titleCallSpec.content()).thenReturn("期中 Java 试卷");
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                aiAgentService,
                chatClient,
                mock(AgentSearchTools.class));
        GenerationRequest generation = new GenerationRequest(
                null,
                10,
                5,
                3,
                null,
                BigDecimal.valueOf(100),
                90,
                "Java 期中测试",
                "期中考试",
                "覆盖集合框架和异常处理",
                null,
                List.of("集合框架", "异常处理"),
                null);

        List<ServerSentEvent<String>> events = service.stream(
                        new ChatRequest(null, "帮我出卷", "PAPER", null, generation),
                        userId,
                        2,
                        null)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("conversation", "generation_result", "chunk", "conversation");
        assertThat(events.getFirst().data()).contains("帮我出卷");
        assertThat(events.getLast().data()).contains("期中 Java 试卷");
        verify(conversationService).updateConversationTitle(conversationId, "期中 Java 试卷", userId);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(titleSpec).user(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("生成任务：出卷")
                .contains("试卷名称：Java 期中测试")
                .contains("试卷类型：期中考试")
                .contains("要求：覆盖集合框架和异常处理")
                .contains("知识点：[集合框架, 异常处理]");
    }

    @Test
    void generationStreamShouldEmitPaperResultPayloadEvent() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationService conversationService = mock(ConversationService.class);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        Map<String, Object> payload = Map.of(
                "schemaVersion", 2,
                "questions", List.of(Map.of(
                        "questionTitle", "HashMap load factor",
                        "questionContent", "Choose the correct answer.")));
        when(aiAgentService.runGeneration(
                any(ChatRequest.class),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new AiAgentResult("paper content", AiMessageType.PAPER, payload));
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                aiAgentService);

        List<ServerSentEvent<String>> events = service.stream(
                        new ChatRequest(conversationId, "generate paper", "PAPER", null, null),
                        userId,
                        2,
                        null)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event).contains("generation_result", "chunk");
        ServerSentEvent<String> resultEvent = events.stream()
                .filter(event -> "generation_result".equals(event.event()))
                .findFirst()
                .orElseThrow();
        Map<String, Object> data = new ObjectMapper().readValue(
                resultEvent.data(),
                new TypeReference<>() {
                });
        assertThat(data)
                .containsEntry("mode", "PAPER")
                .containsEntry("messageType", "PAPER")
                .containsEntry("content", "paper content");
        Map<?, ?> resultPayload = (Map<?, ?>) data.get("payload");
        assertThat(resultPayload.get("schemaVersion")).isEqualTo(2);
        assertThat((List<?>) resultPayload.get("questions")).hasSize(1);
    }

    @Test
    void generationProgressShouldEmitSnapshotAndProgressEvents() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String requestId = "request-1";
        MessageRepository messageRepository = mock(MessageRepository.class);
        ChatMessage message = message(messageId, MessageRole.ASSISTANT, AiMessageType.PAPER, "",
                Map.of(
                        "generationRequestId", requestId,
                        "generationStatus", "processing",
                        "generationStage", "PLANNED"));
        message.setConversationId(conversationId);
        when(messageRepository.findById(messageId)).thenReturn(java.util.Optional.of(message));
        ConversationService conversationService = mock(ConversationService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        when(kafkaBridge.progress(requestId)).thenReturn(reactor.core.publisher.Flux.just(
                new QuestionGenerationProgressEvent(
                        UUID.randomUUID(),
                        requestId,
                        "generation_stage",
                        Map.of("event", Map.of(
                                "requestId", requestId,
                                "mode", "PAPER",
                                "stage", "GENERATED",
                                "status", "processing",
                                "title", "Draft generated",
                                "summary", "Generated 1 draft question.",
                                "payload", Map.of("questionCount", 1))),
                        Instant.now())));
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                mock(AiAgentService.class),
                kafkaBridgeProvider(kafkaBridge));

        List<ServerSentEvent<String>> events = service.streamGenerationProgress(conversationId, messageId, userId)
                .collectList()
                .block();

        verify(conversationService).requireOwnedConversation(conversationId, userId);
        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("generation_snapshot", "generation_stage");
        Map<String, Object> snapshot = new ObjectMapper().readValue(events.getFirst().data(), new TypeReference<>() {
        });
        assertThat(snapshot).containsEntry("id", messageId.toString());
        Map<String, Object> stage = new ObjectMapper().readValue(events.get(1).data(), new TypeReference<>() {
        });
        assertThat(stage)
                .containsEntry("messageId", messageId.toString())
                .containsEntry("requestId", requestId)
                .containsEntry("stage", "GENERATED");
    }

    @Test
    void generationProgressShouldOnlyEmitSnapshotWhenMessageCompleted() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        ChatMessage message = message(messageId, MessageRole.ASSISTANT, AiMessageType.QUESTION_SET, "done",
                Map.of(
                        "generationRequestId", "request-1",
                        "generationStatus", "completed",
                        "generationStage", "RESPONDED"));
        message.setConversationId(conversationId);
        when(messageRepository.findById(messageId)).thenReturn(java.util.Optional.of(message));
        ConversationService conversationService = mock(ConversationService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                mock(AiAgentService.class),
                kafkaBridgeProvider(kafkaBridge));

        List<ServerSentEvent<String>> events = service.streamGenerationProgress(conversationId, messageId, userId)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event).containsExactly("generation_snapshot");
        verify(kafkaBridge, never()).progress(any());
    }

    @Test
    void terminateGenerationShouldPersistTerminatedStateAndCancelBridge() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String requestId = "request-1";
        MessageRepository messageRepository = mock(MessageRepository.class);
        ChatMessage message = message(messageId, MessageRole.ASSISTANT, AiMessageType.PAPER, "",
                Map.of(
                        "generationRequestId", requestId,
                        "generationMode", "PAPER",
                        "generationStatus", "processing",
                        "generationStage", "PLANNED",
                        "generationTrace", List.of()));
        message.setConversationId(conversationId);
        when(messageRepository.findById(messageId)).thenReturn(java.util.Optional.of(message));
        ConversationService conversationService = mock(ConversationService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        QuestionGenerationCancellationService cancellationService = mock(QuestionGenerationCancellationService.class);
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                mock(AiAgentService.class),
                kafkaBridgeProvider(kafkaBridge),
                cancellationService);

        service.terminateGeneration(conversationId, messageId, userId);

        verify(conversationService).requireOwnedConversation(conversationId, userId);
        verify(cancellationService).cancel(requestId);
        verify(kafkaBridge).cancel(requestId, com.dayz.sc.ai.model.enums.AiAgentMode.PAPER);
        verify(messageRepository).update(argThat(updated ->
                "terminated".equals(updated.getPayload().get("generationStatus"))
                        && "TERMINATED".equals(updated.getPayload().get("generationStage"))));
    }

    @Test
    void generationStreamDisconnectShouldNotCancelQueuedKafkaGeneration() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findById(any())).thenAnswer(invocation -> {
            ChatMessage message = new ChatMessage();
            message.setId(invocation.getArgument(0));
            message.setConversationId(conversationId);
            message.setRole(MessageRole.ASSISTANT.name());
            message.setContent("");
            message.setMessageType(AiMessageType.PAPER.name());
            message.setPayload(Map.of("generationStatus", "processing", "generationStage", "RECEIVED"));
            return Optional.of(message);
        });
        ConversationService conversationService = mock(ConversationService.class);
        QuestionGenerationKafkaBridge kafkaBridge = mock(QuestionGenerationKafkaBridge.class);
        when(kafkaBridge.submit(
                any(ChatRequest.class),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(reactor.core.publisher.Mono.never());
        when(kafkaBridge.progress(any())).thenReturn(reactor.core.publisher.Flux.never());
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                mock(AiAgentService.class),
                kafkaBridgeProvider(kafkaBridge));

        service.stream(new ChatRequest(conversationId, "generate paper", "PAPER", null, null), userId, 2, null)
                .take(1)
                .collectList()
                .block();

        verify(kafkaBridge, never()).cancel(any(), any());
        verify(messageRepository, never()).update(argThat(message ->
                "terminated".equals(message.getPayload().get("generationStatus"))
                        || "TERMINATED".equals(message.getPayload().get("generationStage"))));
    }

    @Test
    void generationStreamShouldUpdatePersistedPlaceholderMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findById(any())).thenAnswer(invocation -> {
            ChatMessage message = new ChatMessage();
            message.setId(invocation.getArgument(0));
            message.setConversationId(conversationId);
            message.setRole(MessageRole.ASSISTANT.name());
            message.setContent("");
            message.setMessageType(AiMessageType.QUESTION_SET.name());
            message.setPayload(Map.of("generationStatus", "processing", "generationTrace", List.of()));
            return java.util.Optional.of(message);
        });
        ConversationService conversationService = mock(ConversationService.class);
        AiAgentService aiAgentService = mock(AiAgentService.class);
        Map<String, Object> payload = Map.of("schemaVersion", 2, "questions", List.of());
        when(aiAgentService.runGeneration(
                any(ChatRequest.class),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new AiAgentResult("generated", AiMessageType.QUESTION_SET, payload));
        RagChatService service = serviceWith(
                mock(VectorStore.class),
                new AiProperties(),
                messageRepository,
                conversationService,
                aiAgentService);

        service.stream(new ChatRequest(conversationId, "generate questions", "QUESTION", null, null), userId, 2, null)
                .collectList()
                .block();

        ArgumentCaptor<ChatMessage> savedCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository, times(2)).save(savedCaptor.capture());
        assertThat(savedCaptor.getAllValues())
                .filteredOn(message -> MessageRole.ASSISTANT.name().equals(message.getRole()))
                .hasSize(1)
                .first()
                .satisfies(message -> assertThat(message.getPayload())
                        .containsEntry("generationStatus", "processing")
                        .containsEntry("generationStage", "RECEIVED"));
        verify(messageRepository).update(argThat(message ->
                MessageRole.ASSISTANT.name().equals(message.getRole())
                        && "generated".equals(message.getContent())
                        && AiMessageType.QUESTION_SET.name().equals(message.getMessageType())
                        && "completed".equals(message.getPayload().get("generationStatus"))
                        && "RESPONDED".equals(message.getPayload().get("generationStage"))
                        && Integer.valueOf(2).equals(message.getPayload().get("schemaVersion"))));
    }

    @Test
    void chatModeShouldCallModelWithAgentSearchToolsWithoutStreamingToolAggregation() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findRecentByConversationId(conversationId, 13)).thenReturn(List.of());
        ConversationService conversationService = mock(ConversationService.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        AgentSearchTools agentSearchTools = mock(AgentSearchTools.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("我是李文昊，角色是学生。");
        when(requestSpec.toolContext(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> context = invocation.getArgument(0);
            AgentSearchEventEmitter emitter =
                    (AgentSearchEventEmitter) context.get(AgentSearchTools.CONTEXT_EVENT_EMITTER);
            AgentSearchEvent started = AgentSearchEvent.started("courses", "正在检索课程", "课程");
            emitter.emit(started);
            emitter.emit(AgentSearchEvent.outcome(
                    started.searchId(),
                    AgentSearchOutcome.ok(
                            "courses",
                            "platform",
                            "课程",
                            "找到 1 门课程",
                            18L,
                            List.of(new com.dayz.sc.common.feign.dto.AgentSearchItem(
                                    "COURSE",
                                    "课程",
                                    "course-a",
                                    "course-a",
                                    "AI Course",
                                    "主讲课程",
                                    "课程简介",
                                    "主讲课程",
                                    Map.of("status", 1),
                                    Map.of("sourceType", "COURSE", "sourceId", "course-a", "courseId", "course-a"))))));
            return requestSpec;
        });
        RagChatService service = serviceWith(
                vectorStore,
                new AiProperties(),
                messageRepository,
                conversationService,
                mock(AiAgentService.class),
                chatClient,
                agentSearchTools);

        List<ServerSentEvent<String>> events = service.stream(
                        new ChatRequest(conversationId, "你知道我是谁吗，从系统里搜索", "CHAT", null, null),
                        userId,
                        2,
                        "Bearer user-token")
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("context", "agent_search", "agent_search", "agent_search", "chunk");
        assertThat(events).anySatisfy(event ->
                assertThat(event.data()).contains("\"phase\":\"started\"", "\"searchId\""));
        assertThat(events).anySatisfy(event ->
                assertThat(event.data()).contains(
                        "\"phase\":\"results\"",
                        "\"status\":\"OK\"",
                        "\"provider\":\"platform\"",
                        "\"durationMs\":18",
                        "\"sourceId\":\"course-a\""));
        assertThat(events.get(3).data()).contains("\"phase\":\"completed\"");
        assertThat(events.getLast().data()).contains("我是李文昊");
        verify(messageRepository, atLeastOnce()).save(argThat(message -> {
            if (!MessageRole.ASSISTANT.name().equals(message.getRole()) || message.getPayload() == null) {
                return false;
            }
            Map<?, ?> agentSearch = (Map<?, ?>) message.getPayload().get("agentSearch");
            List<?> searches = agentSearch == null ? Collections.emptyList() : (List<?>) agentSearch.get("searches");
            List<?> items = agentSearch == null ? Collections.emptyList() : (List<?>) agentSearch.get("items");
            return agentSearch != null
                    && ((List<?>) agentSearch.get("events")).size() == 3
                    && searches.size() == 1
                    && items.size() == 1
                    && searches.getFirst().toString().contains("course-a")
                    && searches.getFirst().toString().contains("status=OK")
                    && searches.getFirst().toString().contains("provider=platform")
                    && searches.getFirst().toString().contains("durationMs=18")
                    && items.getFirst().toString().contains("sourceId=course-a");
        }));
        verify(requestSpec).tools(agentSearchTools);
        verify(requestSpec).toolContext(argThat(context ->
                userId.toString().equals(context.get(AgentSearchTools.CONTEXT_USER_ID))
                        && "2".equals(context.get(AgentSearchTools.CONTEXT_USER_ROLE))
                        && "Bearer user-token".equals(context.get(AgentSearchTools.CONTEXT_AUTHORIZATION))
                        && conversationId.toString().equals(context.get("conversationId"))));
        verify(requestSpec).messages(org.mockito.ArgumentMatchers.<List<Message>>argThat(messages ->
                messages.stream().noneMatch(message -> message.getText().contains("Bearer user-token"))));
        verify(agentSearchTools, never()).getCurrentDateTime(any(ToolContext.class));
        verify(requestSpec, never()).stream();
    }

    @Test
    void chatModeShouldPreflightCurrentDateForTimeSensitiveQuestion() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findRecentByConversationId(conversationId, 13)).thenReturn(List.of());
        ConversationService conversationService = mock(ConversationService.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        AgentSearchTools agentSearchTools = mock(AgentSearchTools.class);
        AgentSearchOutcome timeOutcome = systemTimeOutcome("2026-06-24");
        when(agentSearchTools.getCurrentDateTime(any(ToolContext.class))).thenAnswer(invocation -> {
            ToolContext toolContext = invocation.getArgument(0);
            AgentSearchEventEmitter emitter = (AgentSearchEventEmitter) toolContext.getContext()
                    .get(AgentSearchTools.CONTEXT_EVENT_EMITTER);
            AgentSearchEvent started = AgentSearchEvent.started("time", "正在读取当前日期", "当前日期时间");
            emitter.emit(started);
            emitter.emit(AgentSearchEvent.outcome(started.searchId(), timeOutcome));
            return timeOutcome;
        });
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("根据当前日期检索。");
        RagChatService service = serviceWith(
                vectorStore,
                new AiProperties(),
                messageRepository,
                conversationService,
                mock(AiAgentService.class),
                chatClient,
                agentSearchTools);

        List<ServerSentEvent<String>> events = service.stream(
                        new ChatRequest(conversationId, "查一下最新 AI 新闻", "CHAT", null, null),
                        userId,
                        2,
                        null)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("context", "agent_search", "agent_search", "agent_search", "chunk");
        assertThat(events).anySatisfy(event ->
                assertThat(event.data()).contains("\"domain\":\"time\"", "\"phase\":\"started\""));
        assertThat(events).anySatisfy(event ->
                assertThat(event.data()).contains(
                        "\"domain\":\"time\"",
                        "\"phase\":\"results\"",
                        "\"sourceType\":\"SYSTEM_TIME\"",
                        "\"date\":\"2026-06-24\"",
                        "\"provider\":\"server-clock\""));
        verify(agentSearchTools).getCurrentDateTime(any(ToolContext.class));
        verify(requestSpec).messages(org.mockito.ArgumentMatchers.<List<Message>>argThat(messages ->
                messages.getFirst().getText().contains("AgentSearch preflight context")
                        && messages.getFirst().getText().contains("sourceType=SYSTEM_TIME")
                        && messages.getFirst().getText().contains("date=2026-06-24")
                        && messages.getFirst().getText().contains("getCurrentDateTime")
                        && !messages.getFirst().getText().contains("Bearer")));
        verify(messageRepository, atLeastOnce()).save(argThat(message -> {
            if (!MessageRole.ASSISTANT.name().equals(message.getRole()) || message.getPayload() == null) {
                return false;
            }
            Map<?, ?> agentSearch = (Map<?, ?>) message.getPayload().get("agentSearch");
            List<?> searches = agentSearch == null ? Collections.emptyList() : (List<?>) agentSearch.get("searches");
            List<?> items = agentSearch == null ? Collections.emptyList() : (List<?>) agentSearch.get("items");
            return agentSearch != null
                    && searches.toString().contains("domain=time")
                    && searches.toString().contains("status=OK")
                    && items.toString().contains("sourceType=SYSTEM_TIME")
                    && items.toString().contains("date=2026-06-24");
        }));
    }

    @Test
    void newConversationShouldGenerateTitleAfterAnswerCompletes() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findRecentByConversationId(conversationId, 13)).thenReturn(List.of());
        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.createConversation(
                org.mockito.ArgumentMatchers.anyString(),
                any())).thenReturn(conversationId);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec answerSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.ChatClientRequestSpec titleSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec answerCallSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient.CallResponseSpec titleCallSpec = mock(ChatClient.CallResponseSpec.class);
        AtomicInteger promptIndex = new AtomicInteger();
        when(chatClient.prompt()).thenAnswer(invocation -> promptIndex.getAndIncrement() == 0 ? answerSpec : titleSpec);
        when(answerSpec.call()).thenReturn(answerCallSpec);
        when(titleSpec.call()).thenReturn(titleCallSpec);
        when(answerCallSpec.content()).thenReturn("answer");
        when(titleCallSpec.content()).thenReturn("better title");
        RagChatService service = serviceWith(
                vectorStore,
                new AiProperties(),
                messageRepository,
                conversationService,
                mock(AiAgentService.class),
                chatClient,
                mock(AgentSearchTools.class));

        List<ServerSentEvent<String>> events = service.stream(
                        new ChatRequest(null, "question", "CHAT", null, null),
                        userId,
                        1,
                        null)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("conversation", "context", "agent_search", "chunk", "conversation");
        assertThat(events.get(3).data()).isEqualTo("answer");
        assertThat(events.getLast().data()).contains("better title");
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore, AiProperties properties) {
        return serviceWith(vectorStore, properties, mock(MessageRepository.class));
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository) {
        return serviceWith(
                vectorStore,
                properties,
                messageRepository,
                mock(ConversationService.class),
                mock(AiAgentService.class));
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository,
                                       ConversationService conversationService,
                                       AiAgentService aiAgentService) {
        return serviceWith(
                vectorStore,
                properties,
                messageRepository,
                conversationService,
                aiAgentService,
                emptyQuestionGenerationKafkaBridgeProvider());
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository,
                                       ConversationService conversationService,
                                       AiAgentService aiAgentService,
                                       ObjectProvider<@org.jspecify.annotations.NonNull QuestionGenerationKafkaBridge> questionGenerationKafkaBridgeProvider) {
        return serviceWith(
                vectorStore,
                properties,
                messageRepository,
                conversationService,
                aiAgentService,
                questionGenerationKafkaBridgeProvider,
                mock(QuestionGenerationCancellationService.class));
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository,
                                       ConversationService conversationService,
                                       AiAgentService aiAgentService,
                                       ObjectProvider<@org.jspecify.annotations.NonNull QuestionGenerationKafkaBridge> questionGenerationKafkaBridgeProvider,
                                       QuestionGenerationCancellationService questionGenerationCancellationService) {
        return serviceWith(
                vectorStore,
                properties,
                messageRepository,
                conversationService,
                aiAgentService,
                mock(ChatClient.class),
                mock(AgentSearchTools.class),
                questionGenerationKafkaBridgeProvider,
                questionGenerationCancellationService);
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository,
                                       ConversationService conversationService,
                                       AiAgentService aiAgentService,
                                       ChatClient chatClient,
                                       AgentSearchTools agentSearchTools) {
        return serviceWith(
                vectorStore,
                properties,
                messageRepository,
                conversationService,
                aiAgentService,
                chatClient,
                agentSearchTools,
                emptyQuestionGenerationKafkaBridgeProvider(),
                mock(QuestionGenerationCancellationService.class));
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository,
                                       ConversationService conversationService,
                                       AiAgentService aiAgentService,
                                       ChatClient chatClient,
                                       AgentSearchTools agentSearchTools,
                                       ObjectProvider<@org.jspecify.annotations.NonNull QuestionGenerationKafkaBridge> questionGenerationKafkaBridgeProvider) {
        return serviceWith(
                vectorStore,
                properties,
                messageRepository,
                conversationService,
                aiAgentService,
                chatClient,
                agentSearchTools,
                questionGenerationKafkaBridgeProvider,
                mock(QuestionGenerationCancellationService.class));
    }

    @SuppressWarnings("unchecked")
    private RagChatService serviceWith(VectorStore vectorStore,
                                       AiProperties properties,
                                       MessageRepository messageRepository,
                                       ConversationService conversationService,
                                       AiAgentService aiAgentService,
                                       ChatClient chatClient,
                                       AgentSearchTools agentSearchTools,
                                       ObjectProvider<@org.jspecify.annotations.NonNull QuestionGenerationKafkaBridge> questionGenerationKafkaBridgeProvider,
                                       QuestionGenerationCancellationService questionGenerationCancellationService) {
        ObjectProvider<@org.jspecify.annotations.NonNull VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(vectorStore);
        ChatVectorMemoryService chatVectorMemoryService = mock(ChatVectorMemoryService.class);
        when(chatVectorMemoryService.activeMemoryFilter(any())).thenAnswer(invocation ->
                "%s == '%s' && %s == '%s' && %s == 'false'"
                        .formatted(KnowledgeBaseService.META_USER_ID,
                                invocation.getArgument(0),
                                KnowledgeBaseService.META_SOURCE_TYPE,
                                ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN,
                                ChatVectorMemoryService.META_DELETED));
        when(chatVectorMemoryService.activeMemoryDocuments(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        AiRuntimeGuard aiRuntimeGuard = mock(AiRuntimeGuard.class);
        when(aiRuntimeGuard.isConfigured()).thenReturn(true);
        return new RagChatService(
                chatClient,
                provider,
                messageRepository,
                conversationService,
                properties,
                aiAgentService,
                aiRuntimeGuard,
                mock(PlatformDataTool.class),
                chatVectorMemoryService,
                agentSearchTools,
                new AiProviderCallGuard(),
                new ObjectMapper(),
                questionGenerationKafkaBridgeProvider,
                new GenerationMessageStateService(messageRepository),
                questionGenerationCancellationService
        );
    }

    private ObjectProvider<@org.jspecify.annotations.NonNull QuestionGenerationKafkaBridge> kafkaBridgeProvider(
            QuestionGenerationKafkaBridge kafkaBridge) {
        return new ObjectProvider<>() {
            @Override
            public QuestionGenerationKafkaBridge getObject(Object... args) {
                return kafkaBridge;
            }

            @Override
            public QuestionGenerationKafkaBridge getIfAvailable() {
                return kafkaBridge;
            }

            @Override
            public QuestionGenerationKafkaBridge getObject() {
                return kafkaBridge;
            }
        };
    }

    private ObjectProvider<@org.jspecify.annotations.NonNull QuestionGenerationKafkaBridge> emptyQuestionGenerationKafkaBridgeProvider() {
        return new ObjectProvider<>() {
            @Override
            public QuestionGenerationKafkaBridge getObject(Object... args) {
                return null;
            }

            @Override
            public QuestionGenerationKafkaBridge getIfAvailable() {
                return null;
            }

            @Override
            public QuestionGenerationKafkaBridge getObject() {
                return null;
            }
        };
    }

    private VectorStore vectorStoreWithSourceResults(List<Document> knowledgeDocs, List<Document> chatMemoryDocs) {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            SearchRequest request = invocation.getArgument(0);
            String filter = request.getFilterExpression() == null
                    ? ""
                    : request.getFilterExpression().toString();
            if (filter.contains(ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)) {
                return chatMemoryDocs;
            }
            if (filter.contains(KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC)) {
                return knowledgeDocs;
            }
            return List.of();
        });
        return vectorStore;
    }

    private boolean filterContains(SearchRequest request, String value) {
        return request.getFilterExpression() != null
                && request.getFilterExpression().toString().contains(value);
    }

    private ChatMessage message(UUID id, MessageRole role, AiMessageType messageType, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setRole(role.name());
        message.setMessageType(messageType.name());
        message.setContent(content);
        return message;
    }

    private ChatMessage message(UUID id,
                                MessageRole role,
                                AiMessageType messageType,
                                String content,
                                Map<String, Object> payload) {
        ChatMessage message = message(id, role, messageType, content);
        message.setPayload(payload);
        return message;
    }

    private AgentSearchOutcome systemTimeOutcome(String date) {
        AgentSearchItem item = new AgentSearchItem(
                "SYSTEM_TIME",
                "系统时间",
                date,
                null,
                "当前日期：" + date,
                "Asia/Shanghai",
                "当前日期是 " + date + "，当前时间是 12:00:00，时区 Asia/Shanghai。",
                "系统时间",
                Map.of(
                        "date", date,
                        "time", "12:00:00",
                        "zoneId", "Asia/Shanghai",
                        "instant", date + "T04:00:00Z",
                        "weekday", "WEDNESDAY",
                        "provider", "server-clock"),
                Map.of("date", date, "zoneId", "Asia/Shanghai"));
        return AgentSearchOutcome.ok(
                "time",
                "server-clock",
                "当前日期时间",
                "已读取当前日期",
                1L,
                List.of(item));
    }

    private void assertLatexRules(String prompt) {
        assertThat(prompt)
                .contains("行内公式只使用 `$...$`")
                .contains("前端 KaTeX 支持范围")
                .contains("\\nabla")
                .contains("\\ce{...}")
                .contains("MathJax");
    }
}
