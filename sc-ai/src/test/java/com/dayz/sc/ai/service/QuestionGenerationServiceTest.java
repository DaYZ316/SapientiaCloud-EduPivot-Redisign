package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.model.vo.GenerationTraceEntry;
import com.dayz.sc.ai.model.vo.GenerationValidationIssue;
import com.dayz.sc.ai.model.vo.PaperBlueprint;
import com.dayz.sc.ai.model.vo.PaperSectionPlan;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
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
        assertThat((BigDecimal) options.getFirst().get("score")).isEqualByComparingTo("5");
        assertThat((BigDecimal) options.get(1).get("score")).isEqualByComparingTo("0");
        assertThat(list(questions.getFirst().get("answers"))).isEmpty();
    }

    @Test
    void generateQuestionsShouldRepairObjectiveOptionScoresAtFinalAssembly() {
        ChatFixture fixture = chatFixture(questionJson(1, 1, "多选分值", "请选择正确项"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成 Java 多选题",
                new GenerationRequest(UUID.randomUUID(), 1, 1, 2, BigDecimal.valueOf(6),
                        null, null, null, null, null, null, List.of("集合"), null),
                null);

        List<Map<String, Object>> options = list(questions(result).getFirst().get("options"));
        List<BigDecimal> scores = options.stream()
                .map(option -> (BigDecimal) option.get("score"))
                .toList();
        assertThat(scores)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(BigDecimal.valueOf(3), BigDecimal.valueOf(3), BigDecimal.ZERO);
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
    void generateQuestionsShouldPublishRawAiOutputForAdmin() {
        String rawOutput = questionJson(0, 1, "Admin raw", "Choose admin raw answer");
        ChatFixture fixture = chatFixture(rawOutput);
        QuestionGenerationService service = service(fixture.chatClient());
        List<GenerationStageEvent> events = new ArrayList<>();

        AiAgentResult result = service.generateQuestions(
                "generate admin debug question",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("debug"), null),
                null,
                UUID.randomUUID(),
                0,
                null,
                events::add,
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(events)
                .filteredOn(event -> event.payload() != null
                        && "raw_ai_output".equals(event.payload().get("detailType")))
                .singleElement()
                .satisfies(event -> assertThat(event.payload())
                        .containsEntry("callType", "section_generation")
                        .containsEntry("rawOutput", rawOutput));
        assertThat(debugTrace(result))
                .filteredOn(entry -> "raw_ai_output".equals(entry.detailType()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.payload())
                        .containsEntry("callType", "section_generation")
                        .containsEntry("rawOutput", rawOutput));
    }

    @Test
    void generateQuestionsShouldTreatTitleOnlyModelTextAsQuestionContent() {
        String stem = "Compute the determinant of matrix A = [[1, 0], [0, 1]].";
        String response = """
                {"questions":[{
                  "questionTitle":"%s",
                  "questionType":0,
                  "difficulty":2,
                  "score":5,
                  "estimatedTime":3,
                  "tags":["matrix"],
                  "options":[
                    {"optionLabel":"A","optionContent":"1","isCorrect":1},
                    {"optionLabel":"B","optionContent":"0","isCorrect":0}
                  ],
                  "answers":[]
                }]}
                """.formatted(stem);
        ChatFixture fixture = chatFixture(response);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "generate a matrix question",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("Linear algebra"), null),
                null);

        Map<String, Object> question = questions(result).getFirst();
        assertThat(question)
                .containsEntry("questionTitle", "Linear algebra")
                .containsEntry("questionContent", stem);
        assertThat(question.get("questionTitle")).isNotEqualTo(stem);
    }

    @Test
    void generatePaperShouldBatchMixedTypesByQuestionType() {
        ChatFixture fixture = chatFixture(
                questionJson(0, 3, "single-", "single content-"),
                questionJson(1, 3, "multiple-", "multiple content-"),
                questionJson(3, 3, "blank-", "blank content-"),
                questionJson(4, 2, "short-", "short content-"),
                questionJson(2, 1, "judge-", "judge content-"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "生成一套混合试卷",
                new GenerationRequest(UUID.randomUUID(), 12, 5, 2, null,
                        BigDecimal.valueOf(60), 36, "期中练习", null, null, null, List.of("基础"), null),
                null);

        PaperBlueprint blueprint = (PaperBlueprint) result.payload().get("blueprint");
        assertThat(blueprint.sections()).hasSize(5);
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::questionType)
                .containsExactly(0, 1, 3, 4, 2);
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::targetCount)
                .containsExactly(3, 3, 3, 2, 1);
        assertThat(questions(result)).hasSize(12);
        verify(fixture.callSpec(), times(5)).content();
    }

    @Test
    void generateQuestionsShouldSplitSmallMixedRequestByConcreteType() {
        ChatFixture fixture = chatFixture(
                questionJson(0, 1, "mixed-", "mixed content-"),
                questionJson(1, 1, "mixed-", "mixed content-"),
                questionJson(2, 1, "mixed-", "mixed content-"),
                questionJson(3, 1, "mixed-", "mixed content-"),
                questionJson(4, 1, "mixed-", "mixed content-"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "generate 5 mixed questions",
                new GenerationRequest(UUID.randomUUID(), 5, 5, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("basics"), null),
                null);

        PaperBlueprint blueprint = (PaperBlueprint) result.payload().get("blueprint");
        assertThat(blueprint.sections()).hasSize(5);
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::questionType)
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::targetCount)
                .containsExactly(1, 1, 1, 1, 1);
        assertThat(questions(result)).hasSize(5);
        verify(fixture.callSpec(), times(5)).content();
    }

    @Test
    void generatePaperShouldSplitSmallMixedBlueprintIntoConcreteSections() {
        ChatFixture fixture = chatFixture("not json", "not json", "not json");
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "麦克斯韦方程组",
                new GenerationRequest(UUID.randomUUID(), 3, 5, 1, null,
                        BigDecimal.valueOf(100), 60, "麦克斯韦方程组", null, "麦克斯韦方程组",
                        null, List.of("麦克斯韦方程组"), null),
                null);

        PaperBlueprint blueprint = (PaperBlueprint) result.payload().get("blueprint");
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::questionType)
                .containsExactly(0, 1, 4);
        List<Map<String, Object>> questions = questions(result);
        assertThat(questions).isEmpty();
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("EMPTY_RESULT")
                .doesNotContain("AI_GENERATION_PLACEHOLDER");
        verify(fixture.callSpec(), times(9)).content();
    }

    @Test
    void generatePaperShouldUseQualityBlueprintAndWeightedScoresForSmallMixedPaper() {
        ChatFixture fixture = chatFixture(
                questionJson(0, 1, "single-", "single content-"),
                questionJson(1, 1, "multiple-", "multiple content-"),
                shortAnswerJson("short-1", "short content-1", 5));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "生成 3 题小试卷",
                new GenerationRequest(UUID.randomUUID(), 3, 5, 2, null,
                        BigDecimal.valueOf(100), 60, "小试卷", null, null,
                        null, List.of("基础"), null),
                null);

        PaperBlueprint blueprint = (PaperBlueprint) result.payload().get("blueprint");
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::questionType)
                .containsExactly(0, 1, 4);
        assertThat(questions(result))
                .extracting(question -> question.get("questionType"))
                .containsExactly(0, 1, 4);
        assertThat(questions(result))
                .extracting(question -> (BigDecimal) question.get("score"))
                .satisfies(scores -> assertThat(scores.get(2)).isGreaterThan(scores.get(0)))
                .doesNotContain(new BigDecimal("34"), new BigDecimal("33"));
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("PAPER_TOO_FEW_QUESTIONS");
    }

    @Test
    void generatePaperShouldWarnWhenObjectiveQuestionScoreIsTooHigh() {
        ChatFixture fixture = chatFixture(questionJson(2, 1, "judge-", "judge content-"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "生成高分判断题",
                new GenerationRequest(UUID.randomUUID(), 1, 2, 2, null,
                        BigDecimal.valueOf(20), null, "判断题", null, null, null, null, null),
                null);

        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("JUDGE_SCORE_TOO_HIGH");
    }

    @Test
    void generateQuestionsShouldWarnForWeakMultiSelectDistractors() {
        String weakMulti = """
                {"questions":[{
                  "questionTitle":"弱干扰多选",
                  "questionContent":"请选择正确项。",
                  "questionType":1,
                  "difficulty":2,
                  "score":5,
                  "estimatedTime":3,
                  "tags":["基础"],
                  "options":[
                    {"optionLabel":"A","optionContent":"A","isCorrect":1},
                    {"optionLabel":"B","optionContent":"B","isCorrect":1},
                    {"optionLabel":"C","optionContent":"C","isCorrect":1},
                    {"optionLabel":"D","optionContent":"D","isCorrect":0}
                  ],
                  "answers":[]
                }]}
                """;
        ChatFixture fixture = chatFixture(weakMulti);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成弱干扰多选",
                new GenerationRequest(UUID.randomUUID(), 1, 1, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("基础"), null),
                null);

        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("WEAK_MULTI_SELECT_DISTRACTOR");
    }

    @Test
    void generatePaperShouldWarnWhenKnowledgePointCoverageIsLow() {
        ChatFixture fixture = chatFixture(
                questionJson(0, 1, "single-", "single content-"),
                questionJson(1, 1, "multiple-", "multiple content-"),
                questionJson(4, 1, "short-", "short content-"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "覆盖多个知识点",
                new GenerationRequest(UUID.randomUUID(), 3, 5, 2, null,
                        BigDecimal.valueOf(30), null, "覆盖测试", null, null,
                        null, List.of("函数", "导数", "积分"), null),
                null);

        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("LOW_TOPIC_DIVERSITY");
    }

    @Test
    void generateQuestionsShouldWarnWhenObjectiveAnswersConflictWithOptions() {
        String conflicted = """
                {"questions":[{
                  "questionTitle":"冲突单选",
                  "questionContent":"请选择正确项。",
                  "questionType":0,
                  "difficulty":2,
                  "score":5,
                  "estimatedTime":3,
                  "tags":["基础"],
                  "options":[
                    {"optionLabel":"A","optionContent":"A","isCorrect":0},
                    {"optionLabel":"B","optionContent":"B","isCorrect":1}
                  ],
                  "answers":[{"answerContent":"A","score":5,"sortOrder":1}]
                }]}
                """;
        ChatFixture fixture = chatFixture(conflicted);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成答案冲突单选",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("基础"), null),
                null);

        List<Map<String, Object>> options = list(questions(result).getFirst().get("options"));
        assertThat(options)
                .filteredOn(option -> Integer.valueOf(1).equals(option.get("isCorrect")))
                .extracting(option -> option.get("optionLabel"))
                .containsExactly("B");
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("OBJECTIVE_ANSWER_CONFLICT");
    }

    @Test
    void generateQuestionsShouldWrapBareLatexAnswerDuringFinalRepair() {
        String bareLatexAnswer = """
                {"questions":[{
                  "questionTitle":"定积分计算与对称性拆分",
                  "questionContent":"计算定积分 $\\\\int_{-1}^{1} \\\\frac{x^2 + x + 1}{\\\\sqrt{1 + x^2}} \\\\, \\\\mathrm{d}x$ 的值为 ______。（结果请保留精确形式，包含根式与对数）",
                  "questionType":3,
                  "difficulty":2,
                  "score":10,
                  "estimatedTime":5,
                  "tags":["定积分"],
                  "options":[],
                  "answers":[{
                    "answerContent":"\\\\sqrt{2} + \\\\ln(1 + \\\\sqrt{2})",
                    "explanation":"$\\\\frac{x}{\\\\sqrt{1+x^2}}$ 为奇函数，奇函数在对称区间积分为 0。",
                    "score":10,
                    "sortOrder":1
                  }]
                }]}
                """;
        ChatFixture fixture = chatFixture(bareLatexAnswer);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成定积分填空题",
                new GenerationRequest(UUID.randomUUID(), 1, 3, 2, BigDecimal.valueOf(10),
                        null, null, null, null, null, null, List.of("定积分"), null),
                null);

        List<Map<String, Object>> answers = list(questions(result).getFirst().get("answers"));
        assertThat(answers.getFirst())
                .containsEntry("answerContent", "$\\sqrt{2} + \\ln(1 + \\sqrt{2})$")
                .containsEntry("explanation", "$\\frac{x}{\\sqrt{1+x^2}}$ 为奇函数，奇函数在对称区间积分为 0。");
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .doesNotContain("EMPTY_ANSWER", "MISSING_ANSWERS");
    }

    @Test
    void generatePaperShouldKeepAiScoresWhenScorePerQuestionIsZero() {
        ChatFixture fixture = chatFixture(shortAnswerJson(
                shortAnswer("low score", "low content", 2),
                shortAnswer("high score", "high content", 8)));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "让 AI 自行分配每题分值",
                new GenerationRequest(UUID.randomUUID(), 2, 4, 2, BigDecimal.ZERO,
                        null, null, "AI 分值试卷", null, null, null, null, null),
                null);

        PaperBlueprint blueprint = (PaperBlueprint) result.payload().get("blueprint");
        assertThat(blueprint.totalScore()).isNull();
        assertThat(blueprint.sections())
                .extracting(PaperSectionPlan::scorePerQuestion)
                .containsOnlyNulls();
        assertThat(questions(result))
                .extracting(question -> question.get("score"))
                .containsExactly(new BigDecimal("2"), new BigDecimal("8"));
    }

    @Test
    void generatePaperShouldAllocateTotalScoreUsingAiScoresWhenScorePerQuestionIsZero() {
        ChatFixture fixture = chatFixture(shortAnswerJson(
                shortAnswer("low score", "low content", 2),
                shortAnswer("high score", "high content", 8)));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generatePaper(
                "按 AI 分值权重分配总分",
                new GenerationRequest(UUID.randomUUID(), 2, 4, 2, BigDecimal.ZERO,
                        BigDecimal.valueOf(20), null, "AI 分值试卷", null, null, null, null, null),
                null);

        assertThat(questions(result))
                .extracting(question -> question.get("score"))
                .containsExactly(new BigDecimal("4"), new BigDecimal("16"));
    }

    @Test
    void generateQuestionsShouldKeepInvalidChoiceDraftForReviewAfterRetries() {
        ChatFixture fixture = chatFixture("""
                {"questions":[{"questionTitle":"缺选项","questionContent":"缺选项","questionType":0,"score":5,"estimatedTime":3,"options":[],"answers":[]}]}
                """);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(list(questions(result).getFirst().get("options"))).isEmpty();
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("MISSING_OPTIONS");
        assertThat(debugTrace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()))
                .hasSize(3);
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .hasSize(2);
        verify(fixture.callSpec(), times(5)).content();
    }

    @Test
    void generateQuestionsShouldRunRepairAfterEmptySectionRescue() {
        ChatFixture fixture = chatFixture(
                "{\"questions\":[]}",
                "{\"questions\":[]}",
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
        assertThat(debugTrace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()))
                .hasSize(3);
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        verify(fixture.callSpec(), times(3)).content();
    }

    @Test
    void generateQuestionsShouldKeepEmptyDraftFailuresOutOfVisibleTrace() {
        ChatFixture fixture = chatFixture(
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
                .contains("VALIDATED");
        assertThat(events)
                .extracting(GenerationStageEvent::summary)
                .noneMatch(summary -> summary != null && summary.contains("未生成任何题目"));
        assertThat(trace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()) || "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .extracting(GenerationTraceEntry::detailType)
                .contains("section_attempt")
                .doesNotContain("repair_attempt");
        verify(fixture.callSpec(), times(2)).content();
    }

    @Test
    void generateQuestionsShouldRescueEmptyDraftAfterFirstAttempt() {
        ChatFixture fixture = chatFixture(
                "{\"questions\":[]}",
                questionJson(0, 1, "rescued", "rescued content"));
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "generate single choice",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(1);
        assertThat(debugTrace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()))
                .hasSize(2);
        assertThat(debugTrace(result))
                .filteredOn(entry -> "section_attempt".equals(entry.detailType()))
                .last()
                .satisfies(entry -> assertThat(entry.payload())
                        .containsEntry("questionCount", 1));
        verify(fixture.callSpec(), times(2)).content();
    }

    @Test
    void generateQuestionsShouldPublishDraftProgressPerQuestion() {
        ChatFixture fixture = chatFixture(questionJson(4, 3, "draft", "draft content"));
        QuestionGenerationService service = service(fixture.chatClient());
        List<GenerationStageEvent> events = new ArrayList<>();

        service.generateQuestions(
                "generate three questions",
                new GenerationRequest(UUID.randomUUID(), 3, 4, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null,
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                events::add,
                ignored -> {
                });

        List<GenerationStageEvent> draftProgressEvents = events.stream()
                .filter(event -> "GENERATED".equals(event.stage()))
                .filter(event -> Boolean.TRUE.equals(event.payload().get("questionDelta")))
                .toList();
        assertThat(draftProgressEvents).hasSize(4);
        assertThat(draftProgressEvents.get(0).payload())
                .containsEntry("questionCount", 0)
                .containsEntry("generatedQuestionCount", 0)
                .containsEntry("detailType", "draft_progress");
        assertThat(draftProgressEvents.subList(1, 4))
                .allSatisfy(event -> assertThat((List<?>) event.payload().get("questions")).hasSize(1));
        assertThat(draftProgressEvents.subList(1, 4))
                .allSatisfy(event -> assertThat(event.payload()).containsEntry("detailType", "draft_progress"));
        assertThat(draftProgressEvents.subList(1, 4))
                .extracting(event -> event.payload().get("generatedQuestionCount"))
                .containsExactly(1, 2, 3);
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
                .containsExactly("RECEIVED", "CONTEXT_READY", "PLANNED", "GENERATED", "GENERATED", "GENERATED",
                        "VALIDATED", "REPAIRED", "ASSEMBLED", "RESPONDED");
        assertThat(trace(result))
                .extracting(GenerationTraceEntry::detailType)
                .contains("web_sources", "blueprint", "draft_progress", "validation", "final_questions")
                .doesNotContain("section_attempt", "repair_attempt");
        List<GenerationStageEvent> generatedEvents = events.stream()
                .filter(event -> "GENERATED".equals(event.stage()))
                .toList();
        assertThat(generatedEvents.getFirst().payload())
                .containsEntry("questionCount", 0)
                .containsEntry("generatedQuestionCount", 0)
                .containsEntry("totalQuestionCount", 1);
        assertThat(generatedEvents.get(1).payload().get("questions")).isInstanceOf(List.class);
        assertThat(generatedEvents.get(1).payload())
                .containsEntry("questionCount", 1)
                .containsEntry("generatedQuestionCount", 1)
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
    void generateQuestionsShouldExposeWebSearchSourcesInContextTrace() {
        AgentSearchService agentSearchService = mock(AgentSearchService.class);
        AgentSearchItem source = new AgentSearchItem(
                "WEB_SEARCH",
                "网页",
                "https://www.example.com/java-arrays",
                null,
                "Java arrays guide",
                "https://www.example.com/java-arrays",
                "Array basics for exercises",
                "联网搜索",
                Map.of("url", "https://www.example.com/java-arrays", "favicon", "https://www.example.com/favicon.ico"),
                Map.of());
        when(agentSearchService.searchWeb(anyString(), anyInt()))
                .thenReturn(AgentSearchOutcome.ok("web", "tavily-compatible", "java arrays",
                        "找到 1 条网页结果", 12L, List.of(source)));
        ChatFixture fixture = chatFixture(questionJson(4, 1, "empty-context", "empty context content"));
        QuestionGenerationService service = service(fixture.chatClient(), agentSearchService);
        List<GenerationStageEvent> events = new ArrayList<>();
        List<AgentSearchEvent> agentSearchEvents = new ArrayList<>();

        AiAgentResult result = service.generateQuestions(
                "generate short answer",
                new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.valueOf(10),
                        null, null, null, null, null, null, null, null),
                null,
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                events::add,
                agentSearchEvents::add);

        GenerationStageEvent contextEvent = events.stream()
                .filter(event -> "CONTEXT_READY".equals(event.stage()))
                .findFirst()
                .orElseThrow();
        assertThat(contextEvent.payload())
                .containsEntry("evidenceCount", 1);
        assertThat(contextEvent.title()).isEqualTo("联网搜索资料");
        assertThat((List<?>) contextEvent.payload().get("evidences")).hasSize(1);
        List<Map<String, Object>> webSources = list(contextEvent.payload().get("webSources"));
        assertThat(webSources).singleElement()
                .satisfies(item -> assertThat(item)
                        .containsEntry("site", "example.com")
                        .containsEntry("title", "Java arrays guide")
                        .containsEntry("url", "https://www.example.com/java-arrays")
                        .containsEntry("favicon", "https://www.example.com/favicon.ico"));
        Map<?, ?> agentSearch = (Map<?, ?>) contextEvent.payload().get("agentSearch");
        assertThat((List<?>) agentSearch.get("items")).hasSize(1);
        assertThat(agentSearchEvents).singleElement()
                .satisfies(event -> assertThat(event.domain()).isEqualTo("web"));

        GenerationTraceEntry contextTrace = trace(result).stream()
                .filter(entry -> "web_sources".equals(entry.detailType()))
                .findFirst()
                .orElseThrow();
        assertThat(contextTrace.payload())
                .containsEntry("evidenceCount", 1);
        verify(agentSearchService).searchWeb(anyString(), anyInt());
    }

    @Test
    void sectionPromptShouldUseEmptyPromptEvidencesWhenEvidenceCollectionDisabled() {
        ChatFixture fixture = chatFixture(questionJson(4, 1, "empty-evidence", "empty evidence content"));
        QuestionGenerationService service = service(fixture.chatClient());

        service.generateQuestions(
                "generate matrix short answer",
                new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, List.of("matrix"), null),
                null,
                UUID.randomUUID(),
                2,
                UUID.randomUUID(),
                ignored -> {
                },
                ignored -> {
                });

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(fixture.requestSpec(), atLeastOnce()).user(promptCaptor.capture());
        String sectionPrompt = promptCaptor.getAllValues().getFirst();

        assertThat(sectionPrompt)
                .contains("参考资料 JSON：[]")
                .doesNotContain("RAW_QUESTION_JSON_FIELD_SHOULD_NOT_APPEAR")
                .doesNotContain("QUESTION_SNIPPET_SHOULD_NOT_APPEAR_AS_EVIDENCE")
                .doesNotContain("blocked-live-practice")
                .doesNotContain("blocked-chat-memory");
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
        String invalidDuplicateSortOrder = """
                {"questions":[{"questionTitle":"duplicate sort","questionContent":"fill blanks","questionType":3,"difficulty":2,"score":5,"estimatedTime":3,"options":[],"answers":[{"answerContent":"A","score":2.5,"sortOrder":1},{"answerContent":"B","score":2.5,"sortOrder":1}]}]}
                """;
        ChatFixture fixture = chatFixture(
                invalidDuplicateSortOrder,
                invalidDuplicateSortOrder,
                invalidDuplicateSortOrder,
                """
                        {"questions":[{"questionTitle":"重复排序","questionContent":"请补全公式：____ 与 ____。","questionType":3,"difficulty":2,"score":5,"estimatedTime":3,"options":[],"answers":[{"answerContent":"电场","score":2.5,"sortOrder":1},{"answerContent":"磁场","score":2.5,"sortOrder":1}]}]}
                        """,
                questionJson(3, 1, "修复后的填空", "请补全公式：____ 与 ____。"));
        QuestionGenerationService service = service(fixture.chatClient());

        service.generateQuestions(
                "生成填空题",
                new GenerationRequest(UUID.randomUUID(), 1, 3, 2, BigDecimal.valueOf(5),
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
    void generateQuestionsShouldRejectLegacyOptionFieldsWithoutFallback() {
        String legacy = """
                {"questions":[{"title":"旧字段单选","content":"请选择","type":"single_choice","difficulty":"medium","score":5,"estimatedTime":3,"choices":[{"label":"A","content":"旧答案","correct":true},{"label":"B","content":"干扰项","correct":false}],"answer":[{"content":"A. 旧答案","score":5}]}]}
                """;
        ChatFixture fixture = chatFixture(legacy);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).isEmpty();
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("EMPTY_RESULT");
        assertThat(trace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        verify(fixture.callSpec(), times(3)).content();
    }

    @Test
    void generateQuestionsShouldRejectNestedChineseQuestionFieldsWithoutFallback() {
        String chinese = """
                {"data":{"题目":[{"标题":"中文字段单选","题干":"请选择正确表述","题型":"单选题","难度":"简单","分值":5,"预计用时":3,"选项":[{"序号":"A","内容":"正确表述","是否正确":true},{"序号":"B","内容":"错误表述","是否正确":false}],"答案":[{"内容":"A. 正确表述","分值":5}]}]}}
                """;
        ChatFixture fixture = chatFixture(chinese);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成单选题",
                new GenerationRequest(UUID.randomUUID(), 1, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).isEmpty();
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("EMPTY_RESULT");
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        verify(fixture.callSpec(), times(3)).content();
    }

    @Test
    void generateQuestionsShouldKeepDuplicateWarningFromTriggeringRepair() {
        ChatFixture fixture = chatFixture("""
                {"questions":[
                  {"questionTitle":"重复题","questionContent":"请选择正确答案","questionType":0,"difficulty":2,"score":5,"estimatedTime":3,"options":[{"optionLabel":"A","optionContent":"正确","isCorrect":1},{"optionLabel":"B","optionContent":"错误","isCorrect":0}],"answers":[]},
                  {"questionTitle":"重复题","questionContent":"请选择正确答案","questionType":0,"difficulty":2,"score":5,"estimatedTime":3,"options":[{"optionLabel":"A","optionContent":"正确","isCorrect":1},{"optionLabel":"B","optionContent":"错误","isCorrect":0}],"answers":[]}
                ]}
                """);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成两道单选题",
                new GenerationRequest(UUID.randomUUID(), 2, 0, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).hasSize(2);
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("DUPLICATE_QUESTION");
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        verify(fixture.callSpec(), times(1)).content();
    }

    @Test
    void generateQuestionsShouldNormalizeJudgeOptionsToCorrectAndWrong() {
        String judge = """
                {"questions":[{"questionTitle":"判断题","questionContent":"Java 是跨平台语言。","questionType":2,"difficulty":1,"score":5,"estimatedTime":1,"options":[{"optionLabel":"错误","optionContent":"B","isCorrect":1},{"optionLabel":"正确","optionContent":"A","isCorrect":0}],"answers":[]}]}
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
        assertThat(list(questions(result).getFirst().get("answers"))).isEmpty();
    }

    @Test
    void generateQuestionsShouldRepairBlankQuestionWithEmptyAnswer() {
        String invalidBlank = """
                {"questions":[{"questionTitle":"Servlet 初始化参数","questionContent":"JavaEE 中，Servlet 的初始化参数在 web.xml 中通过 ____ 元素配置。","questionType":3,"difficulty":2,"score":5,"estimatedTime":3,"options":[],"answers":[{"answerContent":"","score":5,"sortOrder":1}]}]}
                """;
        ChatFixture fixture = chatFixture(invalidBlank);
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成填空题",
                new GenerationRequest(UUID.randomUUID(), 1, 3, 2, BigDecimal.valueOf(5),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(list(questions(result).getFirst().get("answers")).getFirst())
                .containsEntry("answerContent", "请教师根据题干补充参考答案。");
        assertThat(issues(result))
                .noneMatch(issue -> "error".equalsIgnoreCase(issue.level()));
        assertThat(trace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        assertThat(debugTrace(result))
                .filteredOn(entry -> "repair_attempt".equals(entry.detailType()))
                .isEmpty();
        verify(fixture.callSpec(), times(1)).content();
    }

    @Test
    void generateQuestionsShouldParseJsonWithUnknownFields() {
        String response = """
                {"provider":"test","questions":[{"id":"ignored","questionTitle":"JVM 内存区域","questionContent":"JVM 堆主要用于存放什么？","questionType":0,"difficulty":2,"score":5,"estimatedTime":3,"tags":["JVM"],"options":[{"optionLabel":"A","optionContent":"对象实例","isCorrect":1,"metadata":{"source":"ai"}},{"optionLabel":"B","optionContent":"线程私有栈帧","isCorrect":0}],"answers":[{"answerContent":"A. 对象实例","score":5}],"metadata":{"chapter":"memory"}}]}
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
    void generateQuestionsShouldReturnEmptyResultWhenModelNeverReturnsQuestions() {
        ChatFixture fixture = chatFixture("not json", "not json", "not json");
        QuestionGenerationService service = service(fixture.chatClient());

        AiAgentResult result = service.generateQuestions(
                "生成简答题",
                new GenerationRequest(UUID.randomUUID(), 1, 4, 2, BigDecimal.valueOf(10),
                        null, null, null, null, null, null, null, null),
                null);

        assertThat(questions(result)).isEmpty();
        assertThat(issues(result))
                .extracting(GenerationValidationIssue::code)
                .contains("EMPTY_RESULT")
                .doesNotContain("DETERMINISTIC_FALLBACK_USED")
                .doesNotContain("AI_GENERATION_PLACEHOLDER");
        verify(fixture.callSpec(), times(3)).content();
    }

    @Test
    void generateQuestionsShouldStripAnswerMarkersFromFormulaOptionContent() {
        String formulaChoice = """
                {"questions":[{"questionTitle":"法拉第定律","questionContent":"选择正确公式。","questionType":0,"difficulty":2,"score":5,"estimatedTime":3,"options":[{"optionLabel":"A","optionContent":"$\\\\nabla \\\\cdot \\\\mathbf{B}=0$","isCorrect":0},{"optionLabel":"B","optionContent":"B. $\\\\nabla \\\\times \\\\mathbf{E}=-\\\\frac{\\\\partial \\\\mathbf{B}}{\\\\partial t}$ （答案）","isCorrect":1}],"answers":[]}]}
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
        assertThat(list(questions(result).getFirst().get("answers"))).isEmpty();
    }

    private QuestionGenerationService service(ChatClient chatClient) {
        AgentSearchService agentSearchService = mock(AgentSearchService.class);
        when(agentSearchService.searchWeb(anyString(), anyInt()))
                .thenAnswer(invocation -> AgentSearchOutcome.empty(
                        "web",
                        "tavily-compatible",
                        invocation.getArgument(0),
                        "未找到匹配网页",
                        0L));
        return service(chatClient, agentSearchService);
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
        Queue<String> queuedResponses = new ArrayDeque<>(List.of(responses));
        when(callSpec.content())
                .thenAnswer(invocation -> queuedResponses.isEmpty() ? "{\"questions\":[]}" : queuedResponses.remove());
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
                .contains("questionTitle is UI preview only")
                .contains("questionContent is the real exam stem")
                .contains("单选题、判断题的正确选项 score 必须等于本题 score")
                .contains("单选题、多选题、判断题的解析优先写在对应选项 explanation 中")
                .contains("questionContent、optionContent、answerContent、explanation 里只要出现数学表达式")
                .contains("例如答案应写成 `$\\sqrt{2} + \\ln(1 + \\sqrt{2})$`")
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

    private String shortAnswerJson(String title, String content, int score) {
        return """
                {"questions":[{
                  "questionTitle":"%s",
                  "questionContent":"%s",
                  "questionType":4,
                  "difficulty":2,
                  "score":%d,
                  "estimatedTime":5,
                  "tags":["基础"],
                  "options":[],
                  "answers":[{"answerContent":"参考答案","score":%d,"sortOrder":1}]
                }]}
                """.formatted(title, content, score, score);
    }

    private String shortAnswerJson(String... questions) {
        return "{\"questions\":[" + String.join(",", questions) + "]}";
    }

    private String shortAnswer(String title, String content, int score) {
        return questionObject(4, title, content)
                .replace("\"score\":5", "\"score\":" + score)
                .replace("\"score\":5,\"sortOrder\":1", "\"score\":" + score + ",\"sortOrder\":1");
    }

    private String mixedQuestionJson(int... types) {
        StringBuilder builder = new StringBuilder("{\"questions\":[");
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(questionObject(types[i], "mixed-" + (i + 1), "mixed content-" + (i + 1)));
        }
        builder.append("]}");
        return builder.toString();
    }

    private String questionObject(int type, String title, String content) {
        if (type <= 2) {
            String options = type == 2
                    ? """
                    [
                      {"optionLabel":"A","optionContent":"正确","isCorrect":1},
                      {"optionLabel":"B","optionContent":"错误","isCorrect":0}
                    ]
                    """
                    : type == 1
                    ? """
                    [
                      {"optionLabel":"A","optionContent":"选项 A","isCorrect":1},
                      {"optionLabel":"B","optionContent":"选项 B","isCorrect":1},
                      {"optionLabel":"C","optionContent":"选项 C","isCorrect":0}
                    ]
                    """
                    : """
                    [
                      {"optionLabel":"A","optionContent":"0.75","isCorrect":1},
                      {"optionLabel":"B","optionContent":"1.0","isCorrect":0}
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
