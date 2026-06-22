package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.model.vo.GenerationTraceEntry;
import com.dayz.sc.ai.model.vo.GenerationValidationIssue;
import com.dayz.sc.ai.model.vo.PaperBlueprint;
import com.dayz.sc.ai.model.vo.PaperSectionPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionGenerationServiceTest {

    @Test
    void generateQuestionsShouldNormalizeDraftsToQuestionCreateShape() {
        UUID bankId = UUID.randomUUID();
        ChatFixture fixture = chatFixture(questionJson(0, 1, "HashMap 默认负载因子", "请选择 HashMap 默认负载因子"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成 Java 单选题",
                new GenerationRequest(bankId, 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("集合"), null),
                null);

        assertThat(result.payload()).containsEntry("schemaVersion", 2);
        List<Map<String, Object>> questions = questions(result);
        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst())
                .containsEntry("questionBankId", bankId.toString())
                .containsEntry("questionType", 0)
                .containsEntry("difficulty", 2)
                .containsEntry("allowPartialCredit", 0)
                .containsKey("imageUrls")
                .doesNotContainKeys("id");
        List<Map<String, Object>> options = list(questions.getFirst().get("options"));
        assertThat(options.getFirst())
                .containsEntry("optionLabel", "A")
                .containsEntry("optionContent", "0.75")
                .containsEntry("isCorrect", 1)
                .containsKey("imageUrls")
                .doesNotContainKeys("label", "content", "correct");
        List<Map<String, Object>> answers = list(questions.getFirst().get("answers"));
        assertThat(answers.getFirst())
                .containsEntry("answerContent", "A. 0.75")
                .containsEntry("sortOrder", 1)
                .doesNotContainKey("content");
    }

    @Test
    void generateQuestionsShouldKeepPreviewOnlyQuestionWhenQuestionBankMissing() {
        ChatFixture fixture = chatFixture(questionJson(0, 1, "Preview only", "Choose preview answer"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "generate preview question",
                new GenerationRequest(null, 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("preview"), null),
                null);

        assertThat(questions(result).getFirst())
                .containsEntry("questionBankId", null)
                .containsEntry("questionTitle", "Preview only1");
        assertThat(issues(result))
                .noneMatch(issue -> "MISSING_QUESTION_BANK_ID".equals(issue.code()));
        verify(fixture.callSpec(), times(1)).content();
    }

    @Test
    void generatePaperShouldSplitMixedTypesIntoSingleQuestionBatches() {
        ChatFixture fixture = chatFixture(
                questionJson(0, 1, "single-1-", "single content-1-"),
                questionJson(0, 1, "single-2-", "single content-2-"),
                questionJson(0, 1, "single-3-", "single content-3-"),
                questionJson(1, 1, "multiple-1-", "multiple content-1-"),
                questionJson(1, 1, "multiple-2-", "multiple content-2-"),
                questionJson(1, 1, "multiple-3-", "multiple content-3-"),
                questionJson(2, 1, "judge-1-", "judge content-1-"),
                questionJson(2, 1, "judge-2-", "judge content-2-"),
                questionJson(3, 1, "blank-1-", "blank content-1-"),
                questionJson(3, 1, "blank-2-", "blank content-2-"),
                questionJson(4, 1, "short-1-", "short content-1-"),
                questionJson(4, 1, "short-2-", "short content-2-"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "生成一套混合试卷",
                new GenerationRequest(UUID.randomUUID(), 12, 5, 2, null,
                        BigDecimal.valueOf(60), 36, "期中练习", null, null, null, List.of("基础"), null),
                null);

        PaperBlueprint blueprint = (PaperBlueprint) result.payload().get("blueprint");
        assertThat(blueprint.sections()).hasSize(12);
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::questionType)
                .containsExactly(0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 4, 4);
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::targetCount)
                .containsOnly(1);
        assertThat(questions(result)).hasSize(12);
        verify(fixture.callSpec(), times(12)).content();
    }

    @Test
    void generateQuestionsShouldRetrySectionWhenBlockingIssueExists() {
        ChatFixture fixture = chatFixture(
                """
                        {"questions":[{"questionTitle":"缺选项","questionContent":"缺选项","questionType":0,"score":5,"estimatedTime":3,"options":[],"answers":[]}]}
                        """,
                questionJson(0, 1, "修复后的单选", "请选择正确答案"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(trace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()))
                .hasSize(2);
        verify(fixture.callSpec(), times(2)).content();
    }

    @Test
    void generateQuestionsShouldRunRepairAfterSectionRetriesExhausted() {
        String invalid = """
                {"questions":[{"questionTitle":"缺选项","questionContent":"缺选项","questionType":0,"score":5,"estimatedTime":3,"options":[],"answers":[]}]}
                """;
        ChatFixture fixture = chatFixture(
                invalid,
                invalid,
                invalid,
                questionJson(0, 1, "整体修复后的单选", "请选择正确答案"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(issues(result))
                .noneMatch(issue -> "error".equalsIgnoreCase(issue.level()));
        assertThat(trace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .hasSize(1);
        verify(fixture.callSpec(), times(4)).content();
    }

    @Test
    void generateQuestionsShouldKeepEmptyDraftFailuresOutOfVisibleTrace() {
        ChatFixture fixture = chatFixture(
                "{\"questions\":[]}",
                "{\"questions\":[]}",
                "{\"questions\":[]}",
                questionJson(0, 1, "修复后的单选", "请选择正确答案"));
        QuestionGenerationService service = service(fixture.chatClient());
        List<GenerationStageEvent> events = new ArrayList<>();

        AiAgentResult result = service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null,
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                events::add,
                ignored -> {
                });

        assertThat(questions(result)).hasSize(1);
        assertThat(events)
                .extracting(GenerationStageEvent::stage)
                .doesNotContain("VALIDATED");
        assertThat(events)
                .extracting(GenerationStageEvent::summary)
                .noneMatch(summary -> summary != null && summary.contains("未生成任何题目"));
        assertThat(trace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()) || "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .extracting(GenerationTraceEntry::detailType)
                .contains("section_attempt", "validation", "repair_attempt");
        verify(fixture.callSpec(), times(4)).content();
    }

    @Test
    void generateQuestionsShouldExposeOrderedStageEventsAndTraceDetails() {
        ChatFixture fixture = chatFixture(questionJson(4, 1, "简答", "解释封装"));
        QuestionGenerationService service = service(fixture.chatClient());
        List<GenerationStageEvent> events = new ArrayList<>();

        AiAgentResult result = service.generateQuestions(
                "生成简答题",
                new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.valueOf(10),
                        null, null, null, null, null, null, null, null),
                null,
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                events::add,
                ignored -> {
                });

        assertThat(events)
                .extracting(GenerationStageEvent::stage)
                .containsExactly("RECEIVED", "CONTEXT_READY", "PLANNED", "GENERATED", "GENERATED",
                        "VALIDATED", "REPAIRED", "ASSEMBLED", "RESPONDED");
        assertThat(trace(result))
                .extracting(GenerationTraceEntry::detailType)
                .contains("context_summary", "blueprint", "draft_progress", "validation", "final_questions")
                .doesNotContain("section_attempt", "repair_attempt");
        List<GenerationStageEvent> generatedEvents = events.stream()
                .filter(event -> "GENERATED".equals(event.stage()))
                .toList();
        assertThat(generatedEvents.getFirst().payload().get("questions")).isInstanceOf(List.class);
        assertThat(generatedEvents.getFirst().payload())
                .containsEntry("questionCount", 1)
                .containsEntry("totalQuestionCount", 1);
        GenerationStageEvent generated = generatedEvents.getLast();
        Map<?, ?> draft = ((List<Map<?, ?>>) generated.payload().get("drafts")).getFirst();
        Map<?, ?> question = (Map<?, ?>) draft.get("question");
        assertThat(question.get("questionTitle")).isNotNull();
        assertThat(question.get("options")).isInstanceOf(List.class);
        GenerationStageEvent assembled = events.stream()
                .filter(event -> "ASSEMBLED".equals(event.stage()))
                .findFirst()
                .orElseThrow();
        Map<?, ?> assembledQuestion = ((List<Map<?, ?>>) assembled.payload().get("questions")).getFirst();
        assertThat(assembledQuestion.get("questionTitle")).isNotNull();
        assertThat(assembledQuestion.get("options")).isInstanceOf(List.class);
    }

    @Test
    void generateQuestionsShouldKeepReferenceDuplicateIssueWhenRepairCannotImprove() {
        String duplicate = """
                {"questions":[{"questionTitle":"参考题","questionContent":"说明什么是多态","questionType":4,"difficulty":2,"score":10,"estimatedTime":5,"options":[],"answers":[{"answerContent":"参考答案","score":10,"sortOrder":1}]}]}
                """;
        ChatFixture fixture = chatFixture(duplicate, duplicate, duplicate, duplicate, duplicate);
        AgentSearchService agentSearchService = mock(AgentSearchService.class);
        when(agentSearchService.searchCourseResources(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new com.dayz.sc.common.feign.dto.AgentSearchItem(
                        "QUESTION",
                        "题目",
                        "q-1",
                        "course-1",
                        "参考题",
                        "",
                        "说明什么是多态",
                        "",
                        Map.of(),
                        Map.of())));
        when(agentSearchService.searchPersonalKnowledge(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(agentSearchService.searchChatMemory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        QuestionGenerationService service = service(fixture.chatClient(), agentSearchService);

        AiAgentResult result = service.generateQuestions(
                "生成简答题",
                new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.valueOf(10),
                        null, null, null, null, null, null, null, null),
                null,
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                ignored -> {
                },
                ignored -> {
                });

        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("REFERENCE_DUPLICATE");
    }

    @Test
    void sectionPromptShouldIncludeSharedLatexRules() {
        ChatFixture fixture = chatFixture(questionJson(4, 1, "简答", "写出 $E=mc^2$ 的含义"));
        QuestionGenerationService service = service(fixture.chatClient());

        service.generateQuestions(
                "生成物理公式题",
                new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.valueOf(10),
                        null, null, null, null, null, null, null, null),
                null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.requestSpec(), atLeastOnce()).user(promptCaptor.capture());

        assertLatexRules(promptCaptor.getAllValues().getFirst());
        assertQuestionOutputRules(promptCaptor.getAllValues().getFirst());
    }

    @Test
    void repairPromptShouldIncludeSharedLatexRules() {
        String invalid = """
                {"questions":[{"questionTitle":"缺选项","questionContent":"缺选项","questionType":0,"score":5,"estimatedTime":3,"options":[],"answers":[]}]}
                """;
        ChatFixture fixture = chatFixture(
                invalid,
                invalid,
                invalid,
                questionJson(0, 1, "整体修复后的单选", "请选择正确答案"));
        QuestionGenerationService service = service(fixture.chatClient());

        service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.requestSpec(), atLeastOnce()).user(promptCaptor.capture());
        String repairPrompt = promptCaptor.getAllValues().stream()
                .filter(prompt -> prompt.contains("修复已经生成的题目集合"))
                .findFirst()
                .orElseThrow();

        assertLatexRules(repairPrompt);
        assertQuestionOutputRules(repairPrompt);
    }

    @Test
    void generateQuestionsShouldRejectLegacyOptionFieldsAndUseRepair() {
        String legacy = """
                {"questions":[{"questionTitle":"旧字段单选","questionContent":"请选择","questionType":0,"difficulty":2,"score":5,"estimatedTime":3,"options":[{"label":"A","content":"旧答案","correct":true},{"label":"B","content":"干扰项","correct":false}],"answers":[{"content":"A. 旧答案","score":5}]}]}
                """;
        ChatFixture fixture = chatFixture(
                legacy,
                legacy,
                legacy,
                questionJson(0, 1, "修复后的单选", "请选择正确答案"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(trace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .hasSize(1);
        verify(fixture.callSpec(), times(4)).content();
    }

    @Test
    void generateQuestionsShouldNormalizeJudgeOptionsToCorrectAndWrong() {
        String judge = """
                {"questions":[{"questionTitle":"判断题","questionContent":"Java 是跨平台语言。","questionType":2,"difficulty":1,"score":5,"estimatedTime":1,"options":[{"optionLabel":"错误","optionContent":"B","isCorrect":true},{"optionLabel":"正确","optionContent":"A","isCorrect":false}],"answers":[]}]}
                """;
        ChatFixture fixture = chatFixture(judge);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成判断题",
                new GenerationRequest(UUID.randomUUID(), 1, 2, 1, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        List<Map<String, Object>> options = list(questions(result).getFirst().get("options"));
        assertThat(options)
                .extracting(option -> option.get("optionLabel") + "=" + option.get("optionContent"))
                .containsExactly("A=正确", "B=错误");
        assertThat(options.getFirst()).containsEntry("isCorrect", 0);
        assertThat(options.get(1)).containsEntry("isCorrect", 1);
        assertThat(list(questions(result).getFirst().get("answers")).getFirst())
                .containsEntry("answerContent", "B. 错误");
    }

    @Test
    void generateQuestionsShouldRepairBlankQuestionWithEmptyAnswer() {
        String invalidBlank = """
                {"questions":[{"questionTitle":"Servlet 初始化参数","questionContent":"JavaEE 中，Servlet 的初始化参数在 web.xml 中通过 ____ 元素配置。","questionType":3,"difficulty":2,"score":5,"estimatedTime":3,"options":[],"answers":[{"answerContent":"","score":5,"sortOrder":1}]}]}
                """;
        String repairedBlank = """
                {"questions":[{"questionTitle":"Servlet 初始化参数","questionContent":"JavaEE 中，Servlet 的初始化参数在 web.xml 中通过 ____ 元素配置。","questionType":3,"difficulty":2,"score":5,"estimatedTime":3,"options":[],"answers":[{"answerContent":"init-param","score":5,"sortOrder":1}]}]}
                """;
        ChatFixture fixture = chatFixture(invalidBlank, invalidBlank, invalidBlank, repairedBlank);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成填空题",
                new GenerationRequest(UUID.randomUUID(), 1, 3, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(list(questions(result).getFirst().get("answers")).getFirst())
                .containsEntry("answerContent", "init-param");
        assertThat(trace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .hasSize(1);
    }

    @Test
    void generateQuestionsShouldParseJsonWithUnknownFields() {
        String response = """
                {"provider":"test","questions":[{"id":"ignored","questionTitle":"JVM 内存区域","questionContent":"JVM 堆主要用于存放什么？","questionType":0,"difficulty":2,"score":5,"estimatedTime":3,"tags":["JVM"],"options":[{"optionLabel":"A","optionContent":"对象实例","isCorrect":true,"metadata":{"source":"ai"}},{"optionLabel":"B","optionContent":"线程私有栈帧","isCorrect":false}],"answers":[{"answerContent":"A. 对象实例","score":5}],"metadata":{"chapter":"memory"}}]}
                """;
        ChatFixture fixture = chatFixture(response);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成 JVM 单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(questions(result).getFirst())
                .containsEntry("questionTitle", "JVM 内存区域")
                .doesNotContainKeys("id", "metadata");
        verify(fixture.callSpec(), times(1)).content();
    }

    @Test
    void generateQuestionsShouldReturnVisiblePlaceholderWhenModelNeverReturnsQuestions() {
        ChatFixture fixture = chatFixture("not json", "not json", "not json", "not json", "not json");
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成简答题",
                new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.valueOf(10),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("AI_GENERATION_PLACEHOLDER");
    }

    @Test
    void generateQuestionsShouldStripAnswerMarkersFromFormulaOptionContent() {
        String formulaChoice = """
                {"questions":[{"questionTitle":"法拉第定律","questionContent":"选择正确公式。","questionType":0,"difficulty":2,"score":5,"estimatedTime":3,"options":[{"optionLabel":"A","optionContent":"$\\\\nabla \\\\cdot \\\\mathbf{B}=0$","isCorrect":false},{"optionLabel":"B","optionContent":"B. $\\\\nabla \\\\times \\\\mathbf{E}=-\\\\frac{\\\\partial \\\\mathbf{B}}{\\\\partial t}$ （答案）","isCorrect":true}],"answers":[]}]}
                """;
        ChatFixture fixture = chatFixture(formulaChoice);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成物理单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        List<Map<String, Object>> options = list(questions(result).getFirst().get("options"));
        assertThat(options.get(1).get("optionContent").toString())
                .doesNotContain("答案")
                .doesNotStartWith("B.");
        assertThat(list(questions(result).getFirst().get("answers")).getFirst().get("answerContent").toString())
                .doesNotContain("答案")
                .doesNotContain("B. B.");
    }

    private QuestionGenerationService service(ChatClient chatClient) {
        return service(chatClient, mock(AgentSearchService.class));
    }

    private QuestionGenerationService service(ChatClient chatClient, AgentSearchService agentSearchService) {
        AiRuntimeGuard guard = mock(AiRuntimeGuard.class);
        when(guard.isConfigured()).thenReturn(true);
        PlatformDataTool platformDataTool = mock(PlatformDataTool.class);
        when(platformDataTool.summarize(org.mockito.ArgumentMatchers.any())).thenReturn("");
        return new QuestionGenerationService(
                chatClient,
                guard,
                platformDataTool,
                new ObjectMapper(),
                new AiProviderCallGuard(),
                agentSearchService);
    }

    private ChatFixture chatFixture(String... responses) {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(responses[0], Arrays.copyOfRange(responses, 1, responses.length));
        return new ChatFixture(chatClient, requestSpec, callSpec);
    }

    private void assertLatexRules(String prompt) {
        assertThat(prompt)
                .contains("行内公式只使用 `$...$`")
                .contains("前端 KaTeX 支持范围")
                .contains("\\nabla")
                .contains("\\ce{...}")
                .contains("MathJax");
    }

    private void assertQuestionOutputRules(String prompt) {
        assertThat(prompt)
                .contains("顶层只能是 {\"questions\":[...]}")
                .contains("判断题固定两个选项：A=正确，B=错误")
                .contains("填空题 options 必须返回空数组")
                .contains("optionContent 只写选项正文");
    }

    private String questionJson(int type, int count, String titlePrefix, String contentPrefix) {
        StringBuilder builder = new StringBuilder("{\"questions\":[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                builder.append(',');
            }
            builder.append(questionObject(type, titlePrefix + i, contentPrefix + i));
        }
        builder.append("]}");
        return builder.toString();
    }

    private String questionObject(int type, String title, String content) {
        if (type <= 2) {
            String options = type == 2
                    ? """
                    [
                      {"optionLabel":"A","optionContent":"正确","isCorrect":true},
                      {"optionLabel":"B","optionContent":"错误","isCorrect":false}
                    ]
                    """
                    : type == 1
                    ? """
                    [
                      {"optionLabel":"A","optionContent":"选项 A","isCorrect":true},
                      {"optionLabel":"B","optionContent":"选项 B","isCorrect":true},
                      {"optionLabel":"C","optionContent":"选项 C","isCorrect":false}
                    ]
                    """
                    : """
                    [
                      {"optionLabel":"A","optionContent":"0.75","isCorrect":true},
                      {"optionLabel":"B","optionContent":"1.0","isCorrect":false}
                    ]
                    """;
            return """
                    {
                      "questionTitle":"%s",
                      "questionContent":"%s",
                      "questionType":%d,
                      "difficulty":2,
                      "score":5,
                      "estimatedTime":3,
                      "tags":["基础"],
                      "options":%s,
                      "answers":[{"answerContent":"模型答案会被正确选项覆盖","score":5}]
                    }
                    """.formatted(title, content, type, options);
        }
        return """
                {
                  "questionTitle":"%s",
                  "questionContent":"%s",
                  "questionType":%d,
                  "difficulty":2,
                  "score":5,
                  "estimatedTime":5,
                  "tags":["基础"],
                  "options":[],
                  "answers":[{"answerContent":"参考答案","score":5,"sortOrder":1}]
                }
                """.formatted(title, content, type);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> questions(AiAgentResult result) {
        return (List<Map<String, Object>>) result.payload().get("questions");
    }

    @SuppressWarnings("unchecked")
    private List<GenerationTraceEntry> trace(AiAgentResult result) {
        return (List<GenerationTraceEntry>) result.payload().get("generationTrace");
    }

    @SuppressWarnings("unchecked")
    private List<GenerationTraceEntry> debugTrace(AiAgentResult result) {
        return (List<GenerationTraceEntry>) result.payload().get("generationDebugTrace");
    }

    @SuppressWarnings("unchecked")
    private List<GenerationValidationIssue> issues(AiAgentResult result) {
        return (List<GenerationValidationIssue>) result.payload().get("issues");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private record ChatFixture(ChatClient chatClient,
                               ChatClient.ChatClientRequestSpec requestSpec,
                               ChatClient.CallResponseSpec callSpec) {
    }
}
