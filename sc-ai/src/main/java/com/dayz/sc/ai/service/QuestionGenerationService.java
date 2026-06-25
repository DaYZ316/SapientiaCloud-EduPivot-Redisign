package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.dto.QuestionGenerateAnswerRecord;
import com.dayz.sc.ai.model.dto.QuestionGenerateOptionRecord;
import com.dayz.sc.ai.model.dto.QuestionGeneratePayload;
import com.dayz.sc.ai.model.dto.QuestionGenerateRecord;
import com.dayz.sc.ai.config.LatexPromptRules;
import com.dayz.sc.ai.model.enums.AiAgentMode;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.model.vo.AiAgentResult;
import com.dayz.sc.ai.model.vo.GeneratedQuestionDraft;
import com.dayz.sc.ai.model.vo.GenerationStageEvent;
import com.dayz.sc.ai.model.vo.GenerationTraceEntry;
import com.dayz.sc.ai.model.vo.GenerationValidationIssue;
import com.dayz.sc.ai.model.vo.PaperBlueprint;
import com.dayz.sc.ai.model.vo.PaperSectionPlan;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.question.CreateQuestionRequest;
import com.dayz.sc.common.question.QuestionAnswerRequest;
import com.dayz.sc.common.question.QuestionOptionRequest;
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_QUESTION_COUNT = 10;
    private static final int MAX_PAPER_COUNT = 50;
    private static final int MAX_SECTION_BATCH_SIZE = 5;
    private static final int MAX_SECTION_ATTEMPTS = 3;
    private static final int MAX_REPAIR_ATTEMPTS = 2;
    private static final int ISSUE_SUMMARY_LIMIT = 5;
    private static final int BLOCKED_SIGNATURE_LIMIT = 8;
    private static final int PREVIEW_TITLE_MAX_LENGTH = 24;
    private static final int STEM_LIKE_TITLE_LENGTH = 40;
    private static final int PROMPT_EVIDENCE_LIMIT = 4;
    private static final int PROMPT_EVIDENCE_EXCERPT_MAX_LENGTH = 180;
    private static final int ADMIN_ROLE_CODE = 0;
    private static final List<Integer> MIXED_QUESTION_TYPES = List.of(0, 1, 2, 3, 4);
    private static final Pattern LATEX_COMMAND_PATTERN = Pattern.compile(
            "\\\\(?:sqrt|frac|dfrac|tfrac|ln|log|sin|cos|tan|cot|sec|csc|int|iint|iiint|sum|prod|lim|nabla"
                    + "|partial|mathrm|mathbf|mathbb|mathcal|text|ce|begin|end|cdot|times|div|pm|mp|leq|geq"
                    + "|neq|approx|equiv|infty|alpha|beta|gamma|delta|epsilon|varepsilon|theta|lambda|mu|pi"
                    + "|rho|sigma|phi|varphi|omega|Delta|Omega)\\b");
    private static final Pattern MATH_DELIMITER_PATTERN = Pattern.compile("(?<!\\\\)\\$|\\\\\\(|\\\\\\[");
    private static final Pattern CJK_TEXT_PATTERN = Pattern.compile("[\\p{IsHan}\\u3040-\\u30ff\\uac00-\\ud7af]");
    private static final Pattern LATEX_TEXT_COMMAND_PATTERN = Pattern.compile(
            "\\\\(?:text|mathrm|mathbf|mathbb|mathcal)\\{[^{}]*}");
    private static final Pattern LATEX_COMMAND_NAME_PATTERN = Pattern.compile("\\\\[A-Za-z]+\\*?");
    private static final Pattern LATIN_WORD_PATTERN = Pattern.compile("[A-Za-z]{2,}");
    private static final Pattern LATEX_ROW_SEPARATOR_PATTERN = Pattern.compile("(?<!\\\\)\\\\\\\\");
    private static final String QUESTION_OUTPUT_RULES = """
                出题 JSON 输出硬性规则：
                - 顶层只能是 {"questions":[...]}，不要返回 markdown、解释文本或代码块。
                - questionTitle is UI preview only: use a short knowledge-point title, not the full stem, options, answer, or analysis.
                - questionContent is the real exam stem: it is required, complete, standalone, and used for paper export.
                - If there is only one question statement, put it in questionContent and derive a short questionTitle from the knowledge point.
                - options 内只能使用 optionContent, optionLabel, isCorrect, score, imageUrls, explanation。
                - answers 内只能使用 answerContent, explanation, score, sortOrder。
                - isCorrect 使用 1/0 整数值：1 表示正确，0 表示错误，不要使用 true/false 或 A/B。
                - 单选题、判断题的正确选项 score 必须等于本题 score，错误选项 score 必须为 0；多选题每个正确选项 score 必须大于 0 且所有正确选项 score 之和必须等于本题 score，错误选项 score 必须为 0。
                - 单选题、多选题、判断题的解析优先写在对应选项 explanation 中；若只有正确项有解析，写在正确选项 explanation 中。
                - optionContent 只写选项正文，禁止带 A.、B.、答案、正确答案或“（答案）”等标签。
                - answerContent 只放最终答案；如果答案是公式结果或符号表达式，直接写成可渲染 LaTeX 的 `$...$` 形式，不要把解释性文字混进 answerContent，解释放到 explanation。
                - questionContent、optionContent、answerContent、explanation 里只要出现数学表达式、公式结果或带根式/对数/分式的符号表达式，都要直接输出可渲染 LaTeX，并用 `$...$` 或 `$$...$$` 包裹；不要只写裸的 `\\sqrt{...}`、`\\ln(...)`、`\\frac{...}{...}`。
                - 例如答案应写成 `$\\sqrt{2} + \\ln(1 + \\sqrt{2})$`，不要写成 `\\sqrt{2} + \\ln(1 + \\sqrt{2})`。
                - 判断题固定两个选项：A=正确，B=错误，只通过 isCorrect 标记哪一个正确。
                - 填空题 options 必须返回空数组，answers 必须非空；每个空对应一个非空 answerContent，sortOrder 从 1 开始。
                - 选项里的公式只写公式本身，不要把答案标记拼进公式文本。
                """;

    private final ChatClient chatClient;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final PlatformDataTool platformDataTool;
    private final ObjectMapper objectMapper;
    private final AiProviderCallGuard aiProviderCallGuard;
    private final AgentSearchService agentSearchService;

    public AiAgentResult generateQuestions(String userMessage, GenerationRequest request, AiCourseContext context) {
        return generateQuestions(userMessage, request, context, null, null, null, null, null);
    }

    public AiAgentResult generatePaper(String userMessage, GenerationRequest request, AiCourseContext context) {
        return generatePaper(userMessage, request, context, null, null, null, null, null);
    }

    public AiAgentResult generateQuestions(String userMessage,
                                           GenerationRequest request,
                                           AiCourseContext context,
                                           UUID userId,
                                           Integer role,
                                           UUID courseId,
                                           Consumer<GenerationStageEvent> stageListener,
                                           Consumer<AgentSearchEvent> agentSearchListener) {
        return generateQuestions(userMessage, request, context, userId, role, courseId, stageListener, agentSearchListener, null);
    }

    public AiAgentResult generateQuestions(String userMessage,
                                           GenerationRequest request,
                                           AiCourseContext context,
                                           UUID userId,
                                           Integer role,
                                           UUID courseId,
                                           Consumer<GenerationStageEvent> stageListener,
                                           Consumer<AgentSearchEvent> agentSearchListener,
                                           String requestId) {
        return run(userMessage, request, context, false, userId, role, courseId, stageListener, agentSearchListener, requestId);
    }

    public AiAgentResult generatePaper(String userMessage,
                                       GenerationRequest request,
                                       AiCourseContext context,
                                       UUID userId,
                                       Integer role,
                                       UUID courseId,
                                       Consumer<GenerationStageEvent> stageListener,
                                       Consumer<AgentSearchEvent> agentSearchListener) {
        return generatePaper(userMessage, request, context, userId, role, courseId, stageListener, agentSearchListener, null);
    }

    public AiAgentResult generatePaper(String userMessage,
                                       GenerationRequest request,
                                       AiCourseContext context,
                                       UUID userId,
                                       Integer role,
                                       UUID courseId,
                                       Consumer<GenerationStageEvent> stageListener,
                                       Consumer<AgentSearchEvent> agentSearchListener,
                                       String requestId) {
        return run(userMessage, request, context, true, userId, role, courseId, stageListener, agentSearchListener, requestId);
    }

    private AiAgentResult run(String userMessage,
                              GenerationRequest request,
                              AiCourseContext context,
                              boolean paper,
                              UUID userId,
                              Integer role,
                              UUID courseId,
                              Consumer<GenerationStageEvent> stageListener,
                              Consumer<AgentSearchEvent> agentSearchListener,
                              String suppliedRequestId) {
        AiMessageType messageType = paper ? AiMessageType.PAPER : AiMessageType.QUESTION_SET;
        String mode = paper ? AiAgentMode.PAPER.name() : AiAgentMode.QUESTION.name();
        String requestId = StringUtils.hasText(suppliedRequestId) ? suppliedRequestId : UuidV7Generator.generate().toString();
        GenerationRequest normalized = normalize(request, paper);
        List<GenerationTraceEntry> traceEntries = new ArrayList<>();
        List<GenerationTraceEntry> debugTraceEntries = new ArrayList<>();
        List<AgentSearchEvent> agentSearchEvents = new ArrayList<>();
        boolean exposeRawAiOutput = Integer.valueOf(ADMIN_ROLE_CODE).equals(role);

        emitStage(stageListener, traceEntries, requestId, mode, "RECEIVED", "processing",
                paper ? "接收出卷请求" : "接收出题请求",
                paper ? "已收到试卷生成参数，准备整理课程资料。" : "已收到题目生成参数，准备整理课程资料。",
                requestPayload(normalized),
                "request");

        if (!aiRuntimeGuard.isConfigured()) {
            String message = aiRuntimeGuard.missingKeyMessage();
            emitStage(stageListener, traceEntries, requestId, mode, "FAILED", "error",
                    "AI 服务未配置", message, Map.of("status", "AI_NOT_CONFIGURED"), "failure");
            return new AiAgentResult(message, messageType, Map.of(
                    "status", "AI_NOT_CONFIGURED",
                    "message", message,
                    "generationTrace", traceEntries,
                    "generationDebugTrace", debugTraceEntries));
        }

        AgentSearchOutcome webSearchOutcome = searchGenerationWebSources(userMessage, normalized, paper);
        List<AgentSearchItem> evidences = webSearchOutcome.items();
        agentSearchEvents.add(AgentSearchEvent.outcome(null, webSearchOutcome));
        if (agentSearchListener != null) {
            agentSearchListener.accept(agentSearchEvents.getLast());
        }
        Map<String, Object> contextPayload = evidencePayload(evidences, agentSearchEvents);
        emitStage(stageListener, traceEntries, requestId, mode, "CONTEXT_READY", "processing",
                "联网搜索资料",
                contextSummary(webSearchOutcome),
                contextPayload,
                "web_sources");

        PaperBlueprint blueprint = buildBlueprint(userMessage, normalized, context, evidences, paper, requestId);
        Map<String, Object> blueprintPayload = blueprintPayload(blueprint);
        emitStage(stageListener, traceEntries, requestId, mode, "PLANNED", "processing",
                paper ? "试卷蓝图已生成" : "出题方案已生成",
                blueprint.generationStrategy(),
                blueprintPayload,
                "blueprint");

        Set<String> referenceSignatures = collectReferenceSignatures(evidences);
        List<AgentSearchItem> promptEvidences = promptEvidences(evidences, normalized);
        GenerationDraftResult draftResult = generateDrafts(
                userMessage, normalized, context, promptEvidences, blueprint, paper, traceEntries, debugTraceEntries,
                referenceSignatures, stageListener, requestId, mode, exposeRawAiOutput,
                Math.max(0, evidences.size() - promptEvidences.size()));
        List<CreateQuestionRequest> generatedQuestions = draftResult.questions();
        List<GeneratedQuestionDraft> drafts = draftQuestions(generatedQuestions, draftResult.issues());
        if (generatedQuestions.isEmpty()) {
            emitStage(stageListener, traceEntries, requestId, mode, "GENERATED", "processing",
                    "题目草稿生成中",
                    "正在重新组织题目结构，暂不展示内部重试信息。",
                    Map.of("generatedQuestionCount", 0, "totalQuestionCount", blueprint.totalQuestionCount()),
                    "draft_progress");
        } else {
            emitStage(stageListener, traceEntries, requestId, mode, "GENERATED", "processing",
                    "题目草稿已生成",
                    "已按蓝图生成 " + generatedQuestions.size() + " 道题目草稿。",
                    draftPayload(drafts),
                    "drafts");
        }

        generatedQuestions = rebalanceQuestionScores(generatedQuestions, normalized);
        generatedQuestions = rebalanceEstimatedTimes(generatedQuestions, normalized.totalEstimatedTime());
        List<GenerationValidationIssue> issues = validateQuestions(
                generatedQuestions,
                normalized,
                normalized.questionCount(),
                normalized.totalScore(),
                normalized.totalEstimatedTime(),
                referenceSignatures);
        QuestionGenerationQualityPolicy.QualityReview qualityReview = qualityReview(generatedQuestions, normalized, blueprint, paper);
        issues = mergeIssues(issues, qualityReview.issues());
        appendTraceEntry(debugTraceEntries, "VALIDATED", "qualityReview", "quality_review",
                "出卷质量复核",
                qualityReview.issues().isEmpty()
                        ? "题型、分值、用时和知识点覆盖未发现明显质量问题。"
                        : "发现 " + qualityReview.issues().size() + " 个出卷质量提示。",
                qualityReview.payload());
        Map<String, Object> validationPayload = validationPayload(generatedQuestions, issues);
        if (generatedQuestions.isEmpty()) {
            appendTraceEntry(debugTraceEntries, "VALIDATED", "orchestrator", "validation",
                    "草稿校验结果",
                    "草稿阶段暂未产生可用题目，已转入修复流程。",
                    validationPayload);
        } else {
            emitStage(stageListener, traceEntries, requestId, mode, "VALIDATED", "processing",
                    "题目质量校验完成",
                    issues.isEmpty() ? "未发现结构性问题。" : "发现 " + issues.size() + " 个需要处理的问题。",
                    validationPayload,
                    "validation");
        }

        ReviewResult reviewResult = repairQuestions(
                generatedQuestions, normalized, context, blueprint, issues, referenceSignatures, debugTraceEntries,
                stageListener, requestId, mode, exposeRawAiOutput, paper);
        reviewResult = mergeGenerationWarnings(reviewResult, draftResult.issues());
        List<CreateQuestionRequest> finalQuestions = synchronizeFinalNestedScores(reviewResult.questions());
        List<GenerationValidationIssue> finalIssues = reviewResult.issues();
        emitStage(stageListener, traceEntries, requestId, mode, "REPAIRED", "processing",
                "题目草稿已修正",
                hasBlockingIssues(finalIssues)
                        ? "已完成自动修正，仍有 " + finalIssues.size() + " 个问题需要预览确认。"
                        : "已根据校验结果完成自动修正。",
                questionPayload(finalQuestions, finalIssues),
                "repair_summary");

        Map<String, Object> modelPayload = new LinkedHashMap<>();
        modelPayload.put("title", blueprint.title());
        Map<String, Object> payload = finalPayload(
                modelPayload, normalized, blueprint, finalQuestions, finalIssues,
                traceEntries, debugTraceEntries, agentSearchEvents, paper);
        emitStage(stageListener, traceEntries, requestId, mode, "ASSEMBLED", "processing",
                paper ? "试卷已组装" : "题目集已组装",
                "最终结果包含 " + finalQuestions.size() + " 道题目。",
                questionPayload(finalQuestions, finalIssues),
                "final_questions");

        String content = formatQuestions(payload, paper);
        emitStage(stageListener, traceEntries, requestId, mode, "RESPONDED", "completed",
                "生成完成",
                paper ? "试卷草稿已保存到当前 AI 对话。" : "题目草稿已保存到当前 AI 对话。",
                Map.of("questionCount", finalQuestions.size()),
                "response");
        payload.put("generationTrace", traceEntries);
        payload.put("generationDebugTrace", debugTraceEntries);

        return new AiAgentResult(content, messageType, payload);
    }

    private GenerationRequest normalize(GenerationRequest request, boolean paper) {
        int fallbackCount = paper ? 20 : DEFAULT_QUESTION_COUNT;
        int maxCount = paper ? MAX_PAPER_COUNT : MAX_QUESTION_COUNT;
        int count = request != null && request.questionCount() != null ? request.questionCount() : fallbackCount;
        count = Math.max(1, Math.min(maxCount, count));
        return new GenerationRequest(
                request != null ? request.questionBankId() : null,
                count,
                request != null && request.questionType() != null ? request.questionType() : (paper ? 5 : 0),
                request != null && request.difficulty() != null ? request.difficulty() : 2,
                request != null ? request.scorePerQuestion() : null,
                request != null ? request.totalScore() : null,
                request != null ? request.totalEstimatedTime() : null,
                request != null ? request.paperName() : null,
                request != null ? request.paperType() : null,
                request != null ? request.requirement() : null,
                request != null ? request.chapterIds() : null,
                request != null ? request.knowledgePoints() : null,
                request != null ? request.abilityGoals() : null
        );
    }

    private List<AgentSearchItem> promptEvidences(List<AgentSearchItem> evidences, GenerationRequest request) {
        if (evidences == null || evidences.isEmpty()) {
            return List.of();
        }
        return evidences.stream()
                .filter(this::isPromptEvidence)
                .sorted((left, right) -> Integer.compare(
                        promptEvidencePriority(left, request),
                        promptEvidencePriority(right, request)))
                .limit(PROMPT_EVIDENCE_LIMIT)
                .toList();
    }

    private boolean isPromptEvidence(AgentSearchItem item) {
        if (item == null) {
            return false;
        }
        String sourceType = value(item.sourceType()).toUpperCase(Locale.ROOT);
        if (sourceType.contains("CHAT_MEMORY") || sourceType.contains("LIVE_PRACTICE") || sourceType.contains("PRACTICE")) {
            return false;
        }
        if ("QUESTION".equals(sourceType)) {
            return false;
        }
        return StringUtils.hasText(item.title()) || StringUtils.hasText(item.snippet());
    }

    private int promptEvidencePriority(AgentSearchItem item, GenerationRequest request) {
        String sourceType = value(item.sourceType()).toUpperCase(Locale.ROOT);
        int priority = switch (sourceType) {
            case "KNOWLEDGE_DOC" -> 10;
            case "CHAPTER" -> 20;
            case "COURSE_FILE" -> 30;
            case "COURSE" -> 40;
            case "QUESTION_BANK" -> 70;
            default -> 80;
        };
        if (matchesGenerationFocus(item, request)) {
            priority -= 20;
        }
        return priority;
    }

    private boolean matchesGenerationFocus(AgentSearchItem item, GenerationRequest request) {
        if (request == null || request.knowledgePoints() == null || request.knowledgePoints().isEmpty()) {
            return false;
        }
        String text = (value(item.title()) + " " + value(item.contextLabel()) + " " + value(item.snippet()))
                .toLowerCase(Locale.ROOT);
        return request.knowledgePoints().stream()
                .filter(StringUtils::hasText)
                .map(point -> point.toLowerCase(Locale.ROOT))
                .anyMatch(text::contains);
    }

    private AgentSearchOutcome searchGenerationWebSources(String userMessage, GenerationRequest request, boolean paper) {
        if (agentSearchService == null) {
            return AgentSearchOutcome.disabled("web", "tavily-compatible", generationSearchQuery(userMessage, request, paper),
                    "联网搜索服务未启用");
        }
        String query = generationSearchQuery(userMessage, request, paper);
        try {
            AgentSearchOutcome outcome = agentSearchService.searchWeb(query, PROMPT_EVIDENCE_LIMIT);
            return outcome == null
                    ? AgentSearchOutcome.empty("web", "tavily-compatible", query, "联网搜索未返回结果", null)
                    : outcome;
        } catch (RuntimeException e) {
            return AgentSearchOutcome.failed(
                    "web",
                    "tavily-compatible",
                    query,
                    "联网搜索失败",
                    "联网搜索服务异常",
                    true,
                    null);
        }
    }

    private String generationSearchQuery(String userMessage, GenerationRequest request, boolean paper) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(userMessage)) {
            parts.add(userMessage.strip());
        }
        if (request != null) {
            if (StringUtils.hasText(request.paperName())) {
                parts.add(request.paperName().strip());
            }
            if (StringUtils.hasText(request.requirement())) {
                parts.add(request.requirement().strip());
            }
            if (request.knowledgePoints() != null) {
                request.knowledgePoints().stream()
                        .filter(StringUtils::hasText)
                        .limit(4)
                        .map(String::strip)
                        .forEach(parts::add);
            }
            if (request.abilityGoals() != null) {
                request.abilityGoals().stream()
                        .filter(StringUtils::hasText)
                        .limit(2)
                        .map(String::strip)
                        .forEach(parts::add);
            }
        }
        parts.add(paper ? "试卷 命题 参考资料" : "题目 命题 参考资料");
        return abbreviate(String.join(" ", parts), 240);
    }

    private String contextSummary(AgentSearchOutcome outcome) {
        if (outcome == null) {
            return "联网搜索暂未返回可用资料。";
        }
        if (outcome.status() == AgentSearchStatus.OK && !outcome.items().isEmpty()) {
            return "已联网搜索到 " + outcome.items().size() + " 条可参考网页资料。";
        }
        return StringUtils.hasText(outcome.reason())
                ? outcome.reason()
                : "联网搜索暂未返回可用资料。";
    }

    private PaperBlueprint buildBlueprint(String userMessage,
                                          GenerationRequest request,
                                          AiCourseContext context,
                                          List<AgentSearchItem> evidences,
                                          boolean paper,
                                          String requestId) {
        int totalCount = request.questionCount() == null ? 1 : request.questionCount();
        BigDecimal totalScore = request.totalScore() != null
                ? request.totalScore()
                : scorePerQuestionSpecifiedAsZero(request) ? null : scorePerQuestion(request).multiply(BigDecimal.valueOf(totalCount));
        Integer totalEstimatedTime = request.totalEstimatedTime() != null
                ? request.totalEstimatedTime()
                : recommendedTotalEstimatedTime(request, totalCount, paper);
        List<PaperSectionPlan> sections = paperSections(request, context, evidences, paper, totalCount, totalScore, totalEstimatedTime);
        String title = StringUtils.hasText(request.paperName())
                ? request.paperName().strip()
                : paper ? "AI 试卷草稿" : "AI 题目草稿";
        String strategy = paper
                ? "先依据课程资料制定试卷蓝图，再按题型与知识点分段生成、校验和修复。"
                : "依据课程资料、题库样例和生成要求分段生成题目草稿，并完成结构校验。";
        if (StringUtils.hasText(userMessage)) {
            strategy = strategy + " 生成要求：" + abbreviate(userMessage, 120);
        }
        return new PaperBlueprint(requestId, title, strategy, totalCount, totalScore, totalEstimatedTime, sections);
    }

    private List<PaperSectionPlan> paperSections(GenerationRequest request,
                                                 AiCourseContext context,
                                                 List<AgentSearchItem> evidences,
                                                 boolean paper,
                                                 int totalCount,
                                                 BigDecimal totalScore,
                                                 Integer totalEstimatedTime) {
        List<SectionSeed> seeds = sectionSeeds(request, totalCount, paper);

        List<Integer> timeAllocations = allocateSectionTimes(seeds, request, totalEstimatedTime);
        List<BigDecimal> scoreAllocations = allocateSectionScores(seeds, request, totalScore);
        List<String> knowledgePoints = knowledgePoints(request, context, evidences);
        List<PaperSectionPlan> sections = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            SectionSeed seed = seeds.get(i);
            int sectionNo = i + 1;
            int sectionTime = timeAllocations.get(i);
            int perQuestionTime = Math.max(1, Math.round((float) sectionTime / Math.max(1, seed.count())));
            BigDecimal scorePerQuestion = scoreAllocations.isEmpty()
                    ? null
                    : scoreAllocations.get(i)
                            .divide(BigDecimal.valueOf(Math.max(1, seed.count())), 2, RoundingMode.HALF_UP)
                            .stripTrailingZeros();
            sections.add(new PaperSectionPlan(
                    sectionNo,
                    questionTypeName(seed.questionType()),
                    seed.questionType(),
                    normalizeDifficulty(request.difficulty(), ((sectionNo - 1) % 3) + 1),
                    seed.count(),
                    scorePerQuestion != null && scorePerQuestion.scale() < 0
                            ? scorePerQuestion.setScale(0, RoundingMode.UNNECESSARY)
                            : scorePerQuestion,
                    perQuestionTime,
                    sectionTime,
                    knowledgePoints));
        }
        return sections;
    }

    private List<SectionSeed> sectionSeeds(GenerationRequest request, int totalCount, boolean paper) {
        if (request.questionType() != null && request.questionType() != 5) {
            return singleTypeSeeds(normalizeQuestionType(request.questionType(), 0), totalCount);
        }
        if (paper) {
            return groupQuestionTypes(QuestionGenerationQualityPolicy.paperMixedQuestionTypes(totalCount));
        }
        List<Integer> types = MIXED_QUESTION_TYPES.subList(0, Math.min(totalCount, MIXED_QUESTION_TYPES.size()));
        return evenlySplitTypes(types, totalCount);
    }

    private List<SectionSeed> singleTypeSeeds(int type, int totalCount) {
        List<SectionSeed> seeds = new ArrayList<>();
        int remaining = totalCount;
        while (remaining > 0) {
            int count = Math.min(MAX_SECTION_BATCH_SIZE, remaining);
            seeds.add(new SectionSeed(type, count));
            remaining -= count;
        }
        return seeds;
    }

    private List<SectionSeed> evenlySplitTypes(List<Integer> types, int totalCount) {
        List<SectionSeed> seeds = new ArrayList<>();
        int base = totalCount / types.size();
        int remainder = totalCount % types.size();
        for (int i = 0; i < types.size(); i++) {
            int allocation = base + (i < remainder ? 1 : 0);
            while (allocation > 0) {
                int count = Math.min(MAX_SECTION_BATCH_SIZE, allocation);
                seeds.add(new SectionSeed(types.get(i), count));
                allocation -= count;
            }
        }
        return seeds;
    }

    private List<SectionSeed> groupQuestionTypes(List<Integer> questionTypes) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Integer type : questionTypes) {
            counts.merge(type, 1, Integer::sum);
        }
        List<SectionSeed> seeds = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int remaining = entry.getValue();
            while (remaining > 0) {
                int count = Math.min(MAX_SECTION_BATCH_SIZE, remaining);
                seeds.add(new SectionSeed(entry.getKey(), count));
                remaining -= count;
            }
        }
        return seeds;
    }

    private List<Integer> allocateSectionTimes(List<SectionSeed> seeds, GenerationRequest request, Integer targetTotalTime) {
        List<Integer> weights = new ArrayList<>();
        int recommendedTotal = 0;
        for (int i = 0; i < seeds.size(); i++) {
            SectionSeed seed = seeds.get(i);
            int difficulty = normalizeDifficulty(request.difficulty(), ((i + 1 - 1) % 3) + 1);
            int weight = seed.count() * recommendEstimatedTime(seed.questionType(), difficulty);
            weights.add(weight);
            recommendedTotal += weight;
        }
        int target = targetTotalTime != null && targetTotalTime > 0 ? targetTotalTime : Math.max(1, recommendedTotal);
        List<Integer> minimums = seeds.stream().map(SectionSeed::count).toList();
        return allocateWeightedIntegers(weights, target, minimums);
    }

    private List<BigDecimal> allocateSectionScores(List<SectionSeed> seeds, GenerationRequest request, BigDecimal targetTotalScore) {
        BigDecimal scorePerQuestion = normalizePositiveScore(request.scorePerQuestion());
        if (scorePerQuestionSpecifiedAsZero(request)) {
            return List.of();
        }
        if (scorePerQuestion != null && normalizePositiveScore(request.totalScore()) == null) {
            return seeds.stream()
                    .map(seed -> scorePerQuestion.multiply(BigDecimal.valueOf(seed.count())))
                    .toList();
        }
        BigDecimal target = normalizePositiveScore(targetTotalScore);
        if (target == null) {
            target = BigDecimal.valueOf(seeds.stream().mapToInt(SectionSeed::count).sum())
                    .multiply(scorePerQuestion != null ? scorePerQuestion : BigDecimal.valueOf(5));
        }
        List<Long> weights = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            SectionSeed seed = seeds.get(i);
            int difficulty = normalizeDifficulty(request.difficulty(), ((i + 1 - 1) % 3) + 1);
            weights.add(seed.count() * QuestionGenerationQualityPolicy.scoreWeight(seed.questionType(), difficulty));
        }
        return allocateWeightedScores(weights, target);
    }

    private int recommendedTotalEstimatedTime(GenerationRequest request, int totalCount, boolean paper) {
        if (request.questionType() != null && request.questionType() != 5) {
            return totalCount * recommendEstimatedTime(request.questionType(), request.difficulty());
        }
        if (paper) {
            return QuestionGenerationQualityPolicy.paperMixedQuestionTypes(totalCount).stream()
                    .mapToInt(type -> recommendEstimatedTime(type, request.difficulty()))
                    .sum();
        }
        int total = 0;
        for (int i = 0; i < totalCount; i++) {
            total += recommendEstimatedTime(MIXED_QUESTION_TYPES.get(i % MIXED_QUESTION_TYPES.size()), request.difficulty());
        }
        return Math.max(1, total);
    }

    private GenerationDraftResult generateDrafts(String userMessage,
                                                 GenerationRequest request,
                                                 AiCourseContext context,
                                                 List<AgentSearchItem> evidences,
                                                 PaperBlueprint blueprint,
                                                 boolean paper,
                                                 List<GenerationTraceEntry> traceEntries,
                                                 List<GenerationTraceEntry> debugTraceEntries,
                                                  Set<String> referenceSignatures,
                                                  Consumer<GenerationStageEvent> stageListener,
                                                  String requestId,
                                                  String mode,
                                                  boolean exposeRawAiOutput,
                                                  int discardedEvidenceCount) {
        List<CreateQuestionRequest> questions = new ArrayList<>();
        List<GenerationValidationIssue> allIssues = new ArrayList<>();
        Set<String> blockedSignatures = new LinkedHashSet<>(referenceSignatures);
        publishDraftProgress(stageListener, traceEntries, requestId, mode, null, questions,
                List.of(), List.of(), blueprint.totalQuestionCount());
        for (PaperSectionPlan section : blueprint.sections()) {
            SectionAttempt bestAttempt = null;
            List<String> retryHints = List.of();
            for (int attemptNo = 1; attemptNo <= MAX_SECTION_ATTEMPTS; attemptNo++) {
                SectionAttempt attempt = generateSectionAttempt(
                        userMessage, request, context, evidences, blueprint, section, paper, blockedSignatures,
                        retryHints, attemptNo, debugTraceEntries, stageListener, requestId, mode, exposeRawAiOutput);
                appendTraceEntry(debugTraceEntries, "GENERATED", "questionGeneration", "section_attempt",
                        "第 " + section.sectionNo() + " 部分，第 " + attemptNo + " 次生成",
                        "本次产出 " + attempt.questions().size() + " 道草稿题目，发现 "
                                + attempt.issues().size() + " 个校验问题。",
                        sectionAttemptPayload(section, attemptNo, attempt.questions(), attempt.issues(),
                                evidences.size(), discardedEvidenceCount));
                if (isBetterAttempt(attempt, bestAttempt, section.targetCount())) {
                    bestAttempt = attempt;
                }
                if (!hasBlockingIssues(attempt.issues())) {
                    break;
                }
                retryHints = summarizeIssues(attempt.issues());
            }
            if (bestAttempt != null) {
                bestAttempt = completeSectionAttempt(bestAttempt, section);
                if (hasIssue(bestAttempt.issues(), "QUESTION_COUNT_NORMALIZED")) {
                    appendTraceEntry(debugTraceEntries, "GENERATED", "questionGeneration", "deterministic_fallback",
                            "本部分已自动补齐题目",
                            "模型输出不足时，已用可保存的标准题目结构补齐当前部分。",
                            Map.of(
                                    "sectionNo", section.sectionNo(),
                                    "questionCount", bestAttempt.questions().size(),
                                    "targetCount", section.targetCount(),
                                    "issues", bestAttempt.issues()));
                }
                int previousQuestionCount = questions.size();
                questions.addAll(bestAttempt.questions());
                allIssues.addAll(bestAttempt.issues());
                blockedSignatures.addAll(questionSignatures(bestAttempt.questions()));
                publishDraftProgressByQuestion(stageListener, traceEntries, requestId, mode, section,
                        bestAttempt.questions(), bestAttempt.issues(), blueprint.totalQuestionCount(), previousQuestionCount);
            }
        }
        return new GenerationDraftResult(questions, allIssues);
    }

    private SectionAttempt generateSectionAttempt(String userMessage,
                                                  GenerationRequest request,
                                                  AiCourseContext context,
                                                  List<AgentSearchItem> evidences,
                                                  PaperBlueprint blueprint,
                                                  PaperSectionPlan section,
                                                   boolean paper,
                                                   Set<String> blockedSignatures,
                                                   List<String> retryHints,
                                                   int attemptNo,
                                                   List<GenerationTraceEntry> debugTraceEntries,
                                                   Consumer<GenerationStageEvent> stageListener,
                                                   String requestId,
                                                   String mode,
                                                   boolean exposeRawAiOutput) {
        try {
            QuestionGeneratePayload payload = callSectionModel(
                    userMessage, request, context, evidences, blueprint, section, paper, blockedSignatures,
                    retryHints, attemptNo, debugTraceEntries, stageListener, requestId, mode, exposeRawAiOutput);
            GenerationRequest sectionRequest = sectionRequest(request, section);
            NormalizedQuestionBatch batch = normalizeQuestionsFromRecords(
                    payload == null ? List.of() : payload.questionsOrEmpty(), sectionRequest);
            List<CreateQuestionRequest> questions = batch.questions();
            questions = applySectionDefaults(questions, request, section);
            questions = deterministicRepair(questions, sectionRequest);
            questions = rebalanceQuestionScores(questions, sectionRequest);
            questions = rebalanceEstimatedTimes(questions, section.totalEstimatedTime());
            List<GenerationValidationIssue> issues = validateQuestions(
                    questions, sectionRequest, section.targetCount(), null, section.totalEstimatedTime(), blockedSignatures);
            issues.addAll(batch.issues());
            appendSectionTypeIssues(issues, questions, section.questionType());
            return new SectionAttempt(questions, issues);
        } catch (RuntimeException exception) {
            GenerationValidationIssue issue = new GenerationValidationIssue(
                    "SECTION_GENERATION_FAILED",
                    "error",
                    "本部分生成时模型调用失败。",
                    null,
                    "请重试本部分生成，并严格返回包含 questions 数组的 JSON 对象。");
            return new SectionAttempt(List.of(), List.of(issue));
        }
    }
    private QuestionGeneratePayload callSectionModel(String userMessage,
                                                     GenerationRequest request,
                                                     AiCourseContext context,
                                                     List<AgentSearchItem> evidences,
                                                     PaperBlueprint blueprint,
                                                     PaperSectionPlan section,
                                                      boolean paper,
                                                      Set<String> blockedSignatures,
                                                      List<String> retryHints,
                                                      int attemptNo,
                                                      List<GenerationTraceEntry> debugTraceEntries,
                                                      Consumer<GenerationStageEvent> stageListener,
                                                      String requestId,
                                                      String mode,
                                                      boolean exposeRawAiOutput) {
        String prompt = """
                你是教育测评出题助手。当前任务：只为试卷蓝图中的一个部分生成题目。
                只返回合法 JSON，不要使用 markdown 代码块，不要输出解释。
                顶层结构必须是：{"questions":[...]}。
                questions 数组长度必须等于当前部分 targetCount。

                每道题只输出模型字段，后端会负责注入题库和业务字段：
                - questionTitle, questionContent, questionType, difficulty, score, estimatedTime, tags, options, answers
                - options 内只能使用 optionContent, optionLabel, isCorrect, score, imageUrls, explanation
                - answers 内只能使用 answerContent, explanation, score, sortOrder
                - 单题 questionType 只能是 0-4：0 单选，1 多选，2 判断，3 填空，4 简答
                - 不要生成 questionBankId、id、questionId、allowPartialCredit 或任何业务标识字段
                - difficulty 必须是 1、2、3
                - score 必须为正数，estimatedTime 必须为正整数分钟
                - 单选题、判断题的正确选项 score 必须等于本题 score，错误选项 score 必须为 0；多选题每个正确选项 score 必须大于 0 且所有正确选项 score 之和必须等于本题 score，错误选项 score 必须为 0
                - 单选题、多选题、判断题的解析优先写在对应选项 explanation 中
                - 单选题和判断题必须且只能有一个正确选项；多选题至少两个正确选项；判断题只保留 A/B 两个选项
                - 填空题和简答题 options 返回空数组，并在 answers 中给出可保存的参考答案
                - 不要生成与 blockedSignatures 中题干过于相似的题
                %s
                %s

                生成模式：%s
                第 %s 次尝试，共 %s 次。
                用户要求：%s
                全局生成参数 JSON：%s
                当前蓝图 JSON：%s
                当前部分 JSON：%s
                平台课程资料摘要：%s
                参考资料 JSON：%s
                需要避开的题目签名 JSON：%s
                上次问题提示 JSON：%s
                """.formatted(
                QUESTION_OUTPUT_RULES.stripTrailing(),
                LatexPromptRules.TEXT.stripTrailing(),
                paper ? "paper" : "question_set",
                attemptNo,
                MAX_SECTION_ATTEMPTS,
                value(userMessage),
                toJson(requestPayload(request)),
                toJson(blueprintSummaryPayload(blueprint)),
                toJson(section),
                platformDataTool.summarize(context),
                toJson(summarizePromptEvidences(evidences)),
                toJson(recentBlockedSignatures(blockedSignatures)),
                toJson(retryHints));
        String rawOutput = aiProviderCallGuard.call(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());
        publishRawAiOutput(debugTraceEntries, stageListener, requestId, mode, exposeRawAiOutput,
                "GENERATED",
                "第 " + section.sectionNo() + " 部分，第 " + attemptNo + " 次模型输出",
                "模型已返回当前部分的原始出题内容。",
                rawAiOutputPayload("section_generation", section.sectionNo(), attemptNo, rawOutput));
        return parseQuestionGeneratePayload(rawOutput);
    }
    private List<CreateQuestionRequest> createQuestionsFromRecords(List<QuestionGenerateRecord> records,
                                                                   GenerationRequest request) {
        return normalizeQuestionsFromRecords(records, request).questions();
    }

    private NormalizedQuestionBatch normalizeQuestionsFromRecords(List<QuestionGenerateRecord> records,
                                                                  GenerationRequest request) {
        if (records == null || records.isEmpty()) {
            return new NormalizedQuestionBatch(List.of(), List.of());
        }
        List<CreateQuestionRequest> questions = new ArrayList<>();
        List<GenerationValidationIssue> issues = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            QuestionGenerateRecord record = records.get(i);
            if (record == null) {
                continue;
            }
            if (!StringUtils.hasText(record.questionTitle()) && !StringUtils.hasText(record.questionContent())) {
                continue;
            }
            Map<String, Object> rawQuestion = questionGenerateRecordToMap(record);
            issues.addAll(normalizationIssues(rawQuestion, questions.size() + 1));
            questions.add(normalizeQuestion(rawQuestion, request, i));
        }
        return new NormalizedQuestionBatch(questions, issues);
    }

    private List<GenerationValidationIssue> normalizationIssues(Map<String, Object> rawQuestion, int questionIndex) {
        int type = intValue(rawQuestion.get("questionType"), 4);
        if (type > 2) {
            return List.of();
        }
        Set<String> correctLabels = correctOptionLabels(rawQuestion);
        Set<String> answerLabels = objectiveAnswerLabels(rawQuestion);
        if (correctLabels.isEmpty() || answerLabels.isEmpty() || correctLabels.equals(answerLabels)) {
            return List.of();
        }
        return List.of(QuestionGenerationQualityPolicy.objectiveAnswerConflictIssue(
                questionIndex,
                String.join(",", correctLabels),
                String.join(",", answerLabels)));
    }

    private Set<String> correctOptionLabels(Map<String, Object> rawQuestion) {
        Set<String> labels = new LinkedHashSet<>();
        List<Map<String, Object>> options = mutableMapList(rawQuestion.get("options"));
        for (int i = 0; i < options.size(); i++) {
            Map<String, Object> option = options.get(i);
            if (intValue(option.get("isCorrect"), 0) == 1) {
                labels.add(normalizeOptionLabel(value(option.get("optionLabel")), i));
            }
        }
        return labels;
    }

    private Set<String> objectiveAnswerLabels(Map<String, Object> rawQuestion) {
        Set<String> labels = new LinkedHashSet<>();
        for (Map<String, Object> answer : mutableMapList(rawQuestion.get("answers"))) {
            String compact = value(answer.get("answerContent")).replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (compact.contains("正确") || compact.contains("TRUE")) {
                labels.add("A");
            }
            if (compact.contains("错误") || compact.contains("FALSE")) {
                labels.add("B");
            }
            for (int i = 0; i < compact.length(); i++) {
                char character = compact.charAt(i);
                if (character >= 'A' && character <= 'F') {
                    labels.add(String.valueOf(character));
                }
            }
        }
        return labels;
    }

    private Map<String, Object> questionGenerateRecordToMap(QuestionGenerateRecord question) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("questionBankId", null);
        map.put("questionTitle", question.questionTitle());
        map.put("questionContent", question.questionContent());
        map.put("questionType", question.questionType());
        map.put("difficulty", question.difficulty());
        map.put("score", question.score());
        map.put("estimatedTime", question.estimatedTime());
        map.put("tags", question.tags());
        map.put("imageUrls", List.of());
        map.put("allowPartialCredit", question.questionType() != null && question.questionType() == 1 ? 1 : 0);
        map.put("options", question.options() == null ? List.of() : question.options().stream().map(this::questionGenerateOptionToMap).toList());
        map.put("answers", question.answers() == null ? List.of() : question.answers().stream().map(this::questionGenerateAnswerToMap).toList());
        return map;
    }

    private Map<String, Object> questionGenerateOptionToMap(QuestionGenerateOptionRecord option) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("optionContent", option.optionContent());
        map.put("optionLabel", option.optionLabel());
        map.put("isCorrect", option.isCorrect());
        map.put("score", option.score());
        map.put("imageUrls", option.imageUrls());
        map.put("explanation", option.explanation());
        return map;
    }

    private Map<String, Object> questionGenerateAnswerToMap(QuestionGenerateAnswerRecord answer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("answerContent", answer.answerContent());
        map.put("explanation", answer.explanation());
        map.put("score", answer.score());
        map.put("sortOrder", answer.sortOrder());
        return map;
    }
    private CreateQuestionRequest normalizeQuestion(Map<?, ?> source, GenerationRequest request, int index) {
        Map<String, Object> question = new LinkedHashMap<>();
        int fallbackType = request.questionType() == null || request.questionType() == 5 ? 4 : request.questionType();
        int questionType = normalizeQuestionType(intValue(source.get("questionType"), fallbackType), fallbackType);
        BigDecimal score = decimalValue(source.get("score"), scorePerQuestion(request));
        question.put("questionBankId", questionBankIdValue(source, request));
        question.put("questionTitle", text(source, "questionTitle", ""));
        question.put("questionContent", text(source, "questionContent", ""));
        question.put("questionType", questionType);
        question.put("difficulty", normalizeDifficulty(intValue(source.get("difficulty"), request.difficulty() == null ? 2 : request.difficulty()), 2));
        question.put("score", score);
        question.put("estimatedTime", Math.max(1, intValue(source.get("estimatedTime"), recommendEstimatedTime(questionType, request.difficulty()))));
        question.put("tags", normalizeStringList(source.get("tags"), request.knowledgePoints()));
        question.put("imageUrls", normalizeStringList(source.get("imageUrls"), List.of()));
        question.put("allowPartialCredit", normalizeFlag(intValue(source.get("allowPartialCredit"), questionType == 1 ? 1 : 0)));
        question.put("options", normalizeOptions(source.get("options")));
        question.put("answers", normalizeAnswers(source.get("answers"), score));
        normalizeQuestionTextFields(question, request, index);
        normalizeQuestionStructure(question, request);
        return toCreateQuestionRequest(question);
    }

    private void normalizeQuestionTextFields(Map<String, Object> question, GenerationRequest request, int index) {
        String title = value(question.get("questionTitle"));
        String content = value(question.get("questionContent"));
        if (!StringUtils.hasText(content) && StringUtils.hasText(title)) {
            content = title;
            title = previewQuestionTitle(content, request, index);
        } else if (StringUtils.hasText(content)
                && (!StringUtils.hasText(title) || sameQuestionText(title, content) || looksLikeQuestionStemTitle(title))) {
            title = previewQuestionTitle(content, request, index);
        } else if (!StringUtils.hasText(title)) {
            title = previewQuestionTitle(content, request, index);
        }
        if (!StringUtils.hasText(content)) {
            content = title;
        }
        question.put("questionTitle", title);
        question.put("questionContent", content);
    }

    private String previewQuestionTitle(String source, GenerationRequest request, int index) {
        String knowledgePoint = request == null ? "" : safeList(request.knowledgePoints()).stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        if (StringUtils.hasText(knowledgePoint)) {
            return abbreviate(knowledgePoint, PREVIEW_TITLE_MAX_LENGTH);
        }
        String plain = previewTitleSource(source);
        if (!StringUtils.hasText(plain)) {
            return "题目 " + (index + 1);
        }
        return abbreviate(plain, PREVIEW_TITLE_MAX_LENGTH);
    }

    private String previewTitleSource(String source) {
        return value(source)
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("\\$+", " ")
                .replaceAll("\\\\[A-Za-z]+", " ")
                .replaceAll("[{}_^&]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private boolean sameQuestionText(String left, String right) {
        return value(left).replaceAll("\\s+", " ").equals(value(right).replaceAll("\\s+", " "));
    }

    private boolean looksLikeQuestionStemTitle(String title) {
        String normalized = value(title);
        return normalized.length() > STEM_LIKE_TITLE_LENGTH
                || normalized.contains("\n")
                || normalized.contains("____")
                || normalized.contains("\\begin")
                || normalized.contains("$")
                || normalized.matches(".*[?？].*")
                || (normalized.length() > PREVIEW_TITLE_MAX_LENGTH && normalized.matches(".*[。；;：:].*"));
    }

    private void normalizeQuestionStructure(Map<String, Object> question, GenerationRequest request) {
        int type = intValue(question.get("questionType"), 4);
        BigDecimal score = decimalValue(question.get("score"), scorePerQuestion(request));
        if (type <= 2) {
            List<Map<String, Object>> options = mutableMapList(question.get("options"));
                        question.put("options", options);
            if (type == 2) {
                normalizeJudgeOptions(question);
            } else {
                normalizeChoiceCorrectness(question);
            }
            synchronizeOptionScores(question);
            question.put("answers", List.of());
            return;
        }
        question.put("options", List.of());
        if (!(question.get("answers") instanceof List<?> answers) || answers.isEmpty()) {
            question.put("answers", List.of(Map.of(
                    "answerContent", "请教师根据题干补充参考答案。",
                    "explanation", "",
                    "score", score,
                "sortOrder", 1)));
        }
    }

    private void normalizeChoiceCorrectness(Map<String, Object> question) {
        List<Map<String, Object>> options = mutableMapList(question.get("options"));
        if (options.isEmpty()) {
            return;
        }
        int type = intValue(question.get("questionType"), 0);
        int correctCount = 0;
        for (Map<String, Object> option : options) {
            if (intValue(option.get("isCorrect"), 0) == 1) {
                correctCount++;
            }
        }
        if (type == 1) {
            if (correctCount >= 2) {
                return;
            }
            for (int i = 0; i < options.size(); i++) {
                options.get(i).put("isCorrect", i < Math.min(2, options.size()) ? 1 : 0);
            }
        } else {
            if (correctCount == 1) {
                return;
            }
            for (int i = 0; i < options.size(); i++) {
                options.get(i).put("isCorrect", i == 0 ? 1 : 0);
            }
        }
        question.put("options", options);
    }

    private List<Map<String, Object>> normalizeOptions(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> options = new ArrayList<>();
        int index = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("optionContent", stripOptionContentMarker(text(map, "optionContent", "")));
                option.put("optionLabel", normalizeOptionLabel(text(map, "optionLabel", String.valueOf((char) ('A' + index))), index));
                option.put("isCorrect", normalizeCorrectFlag(map.get("isCorrect")));
                option.put("score", decimalValue(map.get("score"), BigDecimal.ZERO));
                option.put("imageUrls", normalizeStringList(map.get("imageUrls"), List.of()));
                option.put("explanation", text(map, "explanation", ""));
                if (StringUtils.hasText(value(option.get("optionContent")))) {
                    options.add(option);
                    index++;
                }
            }
        }
        return options;
    }

    private List<Map<String, Object>> normalizeAnswers(Object value, Object fallbackScore) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> answers = new ArrayList<>();
        int index = 1;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String content = text(map, "answerContent", "");
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                Map<String, Object> answer = new LinkedHashMap<>();
                answer.put("answerContent", content);
                answer.put("explanation", text(map, "explanation", ""));
                answer.put("score", decimalValue(map.get("score"), decimalValue(fallbackScore, BigDecimal.ZERO)));
                answer.put("sortOrder", Math.max(1, intValue(map.get("sortOrder"), index)));
                answers.add(answer);
                index++;
            }
        }
        return answers;
    }

    private CreateQuestionRequest toCreateQuestionRequest(Map<String, Object> question) {
        return new CreateQuestionRequest(
                uuidValue(question.get("questionBankId")),
                value(question.get("questionTitle")),
                value(question.get("questionContent")),
                intValue(question.get("questionType"), 4),
                normalizeDifficulty(intValue(question.get("difficulty"), 2), 2),
                decimalValue(question.get("score"), BigDecimal.ZERO),
                intValue(question.get("estimatedTime"), 1),
                stringList(question.get("tags")),
                stringList(question.get("imageUrls")),
                normalizeFlag(intValue(question.get("allowPartialCredit"), 0)),
                optionRequests(question.get("options")),
                answerRequests(question.get("answers")));
    }

    private List<CreateQuestionRequest> createQuestionRequests(List<Map<String, Object>> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        return questions.stream()
                .map(this::toCreateQuestionRequest)
                .toList();
    }

    private Map<String, Object> questionMap(CreateQuestionRequest question) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("questionBankId", question.questionBankId() == null ? null : question.questionBankId().toString());
        map.put("questionTitle", question.questionTitle());
        map.put("questionContent", question.questionContent());
        map.put("questionType", question.questionType());
        map.put("difficulty", question.difficulty());
        map.put("score", question.score());
        map.put("estimatedTime", question.estimatedTime());
        map.put("tags", safeList(question.tags()));
        map.put("imageUrls", safeList(question.imageUrls()));
        map.put("allowPartialCredit", question.allowPartialCredit());
        map.put("options", optionMaps(question.options()));
        map.put("answers", answerMaps(question.answers()));
        return map;
    }

    private List<Map<String, Object>> questionMaps(List<CreateQuestionRequest> questions) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        return questions.stream()
                .map(this::questionMap)
                .toList();
    }

    private List<QuestionOptionRequest> optionRequests(Object value) {
        return mutableMapList(value).stream()
                .map(option -> new QuestionOptionRequest(
                        value(option.get("optionContent")),
                        value(option.get("optionLabel")),
                        intValue(option.get("isCorrect"), 0),
                        decimalValue(option.get("score"), BigDecimal.ZERO),
                        stringList(option.get("imageUrls")),
                        value(option.get("explanation"))))
                .toList();
    }

    private List<Map<String, Object>> optionMaps(List<QuestionOptionRequest> options) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (QuestionOptionRequest option : options) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("optionContent", option.optionContent());
            map.put("optionLabel", option.optionLabel());
            map.put("isCorrect", option.isCorrect());
            map.put("score", option.score());
            map.put("imageUrls", safeList(option.imageUrls()));
            map.put("explanation", option.explanation());
            values.add(map);
        }
        return values;
    }

    private List<QuestionAnswerRequest> answerRequests(Object value) {
        return mutableMapList(value).stream()
                .map(answer -> new QuestionAnswerRequest(
                        value(answer.get("answerContent")),
                        value(answer.get("explanation")),
                        decimalValue(answer.get("score"), BigDecimal.ZERO),
                        Math.max(1, intValue(answer.get("sortOrder"), 1))))
                .toList();
    }

    private List<Map<String, Object>> answerMaps(List<QuestionAnswerRequest> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (QuestionAnswerRequest answer : answers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("answerContent", answer.answerContent());
            map.put("explanation", answer.explanation());
            map.put("score", answer.score());
            map.put("sortOrder", answer.sortOrder());
            values.add(map);
        }
        return values;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && StringUtils.hasText(item.toString()))
                .map(item -> item.toString().strip())
                .toList();
    }

    private UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null || !StringUtils.hasText(value.toString())) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void normalizeJudgeOptions(Map<String, Object> question) {
        List<Map<String, Object>> sourceOptions = mutableMapList(question.get("options"));
        boolean trueIsCorrect = inferJudgeAnswer(question, sourceOptions);
        String explanation = firstCorrectOptionExplanation(sourceOptions);
        question.put("options", List.of(
                judgeOption("A", "正确", trueIsCorrect, trueIsCorrect ? explanation : ""),
                judgeOption("B", "错误", !trueIsCorrect, trueIsCorrect ? "" : explanation)));
    }

    private Map<String, Object> judgeOption(String label, String content, boolean correct) {
        return judgeOption(label, content, correct, "");
    }

    private Map<String, Object> judgeOption(String label, String content, boolean correct, String explanation) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("optionLabel", label);
        option.put("optionContent", content);
        option.put("isCorrect", correct ? 1 : 0);
        option.put("score", BigDecimal.ZERO);
        option.put("imageUrls", List.of());
        option.put("explanation", explanation);
        return option;
    }

    private boolean inferJudgeAnswer(Map<String, Object> question, List<Map<String, Object>> options) {
        for (int i = 0; i < options.size(); i++) {
            Map<String, Object> option = options.get(i);
            if (intValue(option.get("isCorrect"), 0) != 1) {
                continue;
            }
            Boolean truthValue = judgeTruthValue(value(option.get("optionLabel")) + " " + value(option.get("optionContent")));
            if (truthValue != null) {
                return truthValue;
            }
            return i == 0;
        }
        for (Map<String, Object> answer : mutableMapList(question.get("answers"))) {
            Boolean truthValue = judgeTruthValue(value(answer.get("answerContent")));
            if (truthValue != null) {
                return truthValue;
            }
        }
        return true;
    }

    private Boolean judgeTruthValue(String text) {
        String normalized = text == null ? "" : text.strip();
        String compact = normalized.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (compact.isEmpty()) {
            return null;
        }
        if (compact.contains("错误") || compact.contains("不正确") || "FALSE".equals(compact)
                || compact.startsWith("B.") || compact.startsWith("B．") || compact.startsWith("B、")
                || "B".equals(compact)) {
            return false;
        }
        if (compact.contains("正确") || "TRUE".equals(compact)
                || compact.startsWith("A.") || compact.startsWith("A．") || compact.startsWith("A、")
                || "A".equals(compact)) {
            return true;
        }
        return null;
    }

    private String normalizeOptionLabel(String label, int index) {
        String fallback = String.valueOf((char) ('A' + index));
        if (!StringUtils.hasText(label)) {
            return fallback;
        }
        String trimmed = label.strip();
        char first = Character.toUpperCase(trimmed.charAt(0));
        if (first >= 'A' && first <= 'Z') {
            return String.valueOf(first);
        }
        return trimmed;
    }

    private String stripOptionContentMarker(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return content.strip()
                .replaceFirst("^[A-Za-z]\\s*[.．、:：]\\s*", "")
                .replaceFirst("^(?:答案|正确答案)\\s*[:：]?\\s*", "")
                .replace("（答案）", "")
                .replace("(答案)", "")
                .strip();
    }

    private String firstCorrectOptionExplanation(Map<String, Object> question) {
        return firstCorrectOptionExplanation(mutableMapList(question.get("options")));
    }

    private String firstCorrectOptionExplanation(List<Map<String, Object>> options) {
        return options.stream()
                .filter(option -> intValue(option.get("isCorrect"), 0) == 1)
                .map(option -> value(option.get("explanation")))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<CreateQuestionRequest> applySectionDefaults(List<CreateQuestionRequest> questions,
                                                             GenerationRequest request,
                                                             PaperSectionPlan section) {
        List<Map<String, Object>> maps = questionMaps(questions);
        applySectionDefaultsMaps(maps, request, section);
        return createQuestionRequests(maps);
    }

    private void applySectionDefaultsMaps(List<Map<String, Object>> questions,
                                           GenerationRequest request,
                                           PaperSectionPlan section) {
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            question.put("questionType", normalizeQuestionType(
                    intValue(question.get("questionType"), sectionQuestionType(section, i)),
                    sectionQuestionType(section, i)));
            question.put("difficulty", normalizeDifficulty(intValue(question.get("difficulty"), section.difficulty()), section.difficulty()));
            question.put("estimatedTime", Math.max(1, intValue(question.get("estimatedTime"), section.estimatedTimePerQuestion())));
            if (section.scorePerQuestion() != null) {
                question.put("score", section.scorePerQuestion());
            }
            normalizeQuestionTextFields(question, request, i);
            if (question.get("tags") instanceof List<?> tags && !tags.isEmpty()) {
                continue;
            }
            question.put("tags", !safeList(section.knowledgePoints()).isEmpty() ? section.knowledgePoints() : safeList(request.knowledgePoints()));
        }
    }

    private SectionAttempt completeSectionAttempt(SectionAttempt attempt,
                                                  PaperSectionPlan section) {
        List<CreateQuestionRequest> questions = new ArrayList<>(attempt.questions());
        List<GenerationValidationIssue> issues = new ArrayList<>(attempt.issues());
        if (questions.size() > section.targetCount()) {
            questions = new ArrayList<>(questions.subList(0, section.targetCount()));
            issues.add(issue(
                    "QUESTION_COUNT_NORMALIZED",
                    "warning",
                    "本部分生成题目数量超过目标数量，已保留前 " + section.targetCount() + " 道题。",
                    null,
                    "请预览确认保留题目是否符合要求。"));
        }
        return new SectionAttempt(questions, issues);
    }

    private int sectionQuestionType(PaperSectionPlan section, int questionIndex) {
        Integer sectionType = section == null ? null : section.questionType();
        if (sectionType != null && sectionType >= 0 && sectionType <= 4) {
            return sectionType;
        }
        return MIXED_QUESTION_TYPES.get(Math.floorMod(questionIndex, MIXED_QUESTION_TYPES.size()));
    }

    private List<GeneratedQuestionDraft> draftQuestions(List<CreateQuestionRequest> questions,
                                                        List<GenerationValidationIssue> issues) {
        List<GeneratedQuestionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            int order = i + 1;
            List<GenerationValidationIssue> draftIssues = issues.stream()
                    .filter(issue -> issue.questionIndex() == null || issue.questionIndex() == order)
                    .toList();
            drafts.add(new GeneratedQuestionDraft(UuidV7Generator.generate().toString(), order, questions.get(i), draftIssues));
        }
        return drafts;
    }

    private List<GenerationValidationIssue> validateQuestions(List<CreateQuestionRequest> questions,
                                                              GenerationRequest request,
                                                              Integer expectedCount,
                                                              BigDecimal expectedTotalScore,
                                                              Integer expectedTotalEstimatedTime,
                                                              Set<String> blockedSignatures) {
        return validateQuestionMaps(questionMaps(questions), request, expectedCount, expectedTotalScore,
                expectedTotalEstimatedTime, blockedSignatures);
    }

    private QuestionGenerationQualityPolicy.QualityReview qualityReview(List<CreateQuestionRequest> questions,
                                                                        GenerationRequest request,
                                                                        PaperBlueprint blueprint,
                                                                        boolean paper) {
        return QuestionGenerationQualityPolicy.review(questionMaps(questions), request, blueprint, paper);
    }

    private List<GenerationValidationIssue> mergeIssues(List<GenerationValidationIssue> base,
                                                        List<GenerationValidationIssue> additions) {
        if (additions == null || additions.isEmpty()) {
            return base == null ? List.of() : base;
        }
        List<GenerationValidationIssue> merged = new ArrayList<>(base == null ? List.of() : base);
        Set<String> existing = issueKeys(merged);
        for (GenerationValidationIssue issue : additions) {
            if (issue != null && existing.add(issueKey(issue))) {
                merged.add(issue);
            }
        }
        return merged;
    }

    private List<GenerationValidationIssue> validateQuestionMaps(List<Map<String, Object>> questions,
                                                              GenerationRequest request,
                                                              Integer expectedCount,
                                                              BigDecimal expectedTotalScore,
                                                              Integer expectedTotalEstimatedTime,
                                                              Set<String> blockedSignatures) {
        List<GenerationValidationIssue> issues = new ArrayList<>();
        if (questions == null || questions.isEmpty()) {
            issues.add(issue("EMPTY_RESULT", "error", "未生成任何题目。", null, "请至少生成一道有效题目。"));
            return issues;
        }
        if (expectedCount != null && questions.size() != expectedCount) {
            issues.add(issue(
                    "QUESTION_COUNT_MISMATCH",
                    "error",
                    "生成题目数量为 " + questions.size() + "，目标数量为 " + expectedCount + "。",
                    null,
                    "请严格返回 " + expectedCount + " 道题目。"));
        }
        if (expectedTotalScore != null) {
            BigDecimal scoreSum = questions.stream()
                    .map(question -> decimalValue(question.get("score"), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (scoreSum.compareTo(expectedTotalScore) != 0) {
                issues.add(issue(
                        "TOTAL_SCORE_MISMATCH",
                        "warning",
                        "题目总分为 " + plain(scoreSum) + "，目标总分为 " + plain(expectedTotalScore) + "。",
                        null,
                        "请调整各题分值，使总分等于 " + plain(expectedTotalScore) + "。"));
            }
        }
        if (expectedTotalEstimatedTime != null) {
            int totalTime = questions.stream()
                    .mapToInt(question -> intValue(question.get("estimatedTime"), 0))
                    .sum();
            if (totalTime != expectedTotalEstimatedTime) {
                issues.add(issue(
                        "TOTAL_ESTIMATED_TIME_MISMATCH",
                        "warning",
                        "预计总用时为 " + totalTime + " 分钟，目标总用时为 " + expectedTotalEstimatedTime + " 分钟。",
                        null,
                        "请调整各题预计用时，使总时长等于 " + expectedTotalEstimatedTime + " 分钟。"));
            }
        }

        Set<String> normalizedBlockedSignatures = blockedSignatures == null ? Set.of() : blockedSignatures;
        Map<String, Integer> duplicateMap = new HashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            int index = i + 1;
            Map<String, Object> question = questions.get(i);
            String title = value(question.get("questionTitle"));
            String content = value(question.get("questionContent"));
            if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) {
                issues.add(issue("EMPTY_CONTENT", "error", "第 " + index + " 题题干为空。", index,
                        "请至少提供题目标题或题目内容。"));
            }
            int type = intValue(question.get("questionType"), -1);
            if (type < 0 || type > 4) {
                issues.add(issue("INVALID_TYPE", "error", "第 " + index + " 题题型无效。", index,
                        "请将题型设置为 0 到 4 之间的有效值。"));
                continue;
            }
            int difficulty = intValue(question.get("difficulty"), -1);
            if (difficulty < 1 || difficulty > 3) {
                issues.add(issue("INVALID_DIFFICULTY", "error", "第 " + index + " 题难度无效。", index,
                        "请将难度设置为 1 到 3 之间的有效值。"));
            }
            if (decimalValue(question.get("score"), BigDecimal.ZERO).compareTo(BigDecimal.ZERO) <= 0) {
                issues.add(issue("INVALID_SCORE", "error", "第 " + index + " 题分值缺失或不为正数。", index,
                        "请提供大于 0 的题目分值。"));
            }
            if (intValue(question.get("estimatedTime"), 0) <= 0) {
                issues.add(issue("INVALID_TIME", "warning", "第 " + index + " 题预计用时缺失或无效。", index,
                        "请提供大于 0 的预计用时。"));
            }
            String signature = buildSignature(question);
            if (StringUtils.hasText(signature)) {
                if (normalizedBlockedSignatures.contains(signature)) {
                    issues.add(issue("REFERENCE_DUPLICATE", "warning",
                            "第 " + index + " 题与现有题目或参考样例过于相似。",
                            index,
                            "请在保持命题要求一致的前提下重写题干，避免重复。"));
                }
                Integer firstIndex = duplicateMap.putIfAbsent(signature, index);
                if (firstIndex != null) {
                    issues.add(issue("DUPLICATE_QUESTION", "warning",
                            "第 " + index + " 题与第 " + firstIndex + " 题内容重复或高度相似。",
                            index,
                            "请重写该题以避免重复。"));
                }
            }
            validateQuestionStructure(question, index, issues);
        }
        return issues;
    }

    private void validateQuestionStructure(Map<String, Object> question,
                                           int index,
                                           List<GenerationValidationIssue> issues) {
        int type = intValue(question.get("questionType"), 4);
        List<Map<String, Object>> options = mutableMapList(question.get("options"));
        List<Map<String, Object>> answers = mutableMapList(question.get("answers"));
        if (type <= 2) {
            if (options.isEmpty()) {
                issues.add(issue("MISSING_OPTIONS", "error", "第 " + index + " 题缺少选项。", index,
                        "选择题需要提供有效选项。"));
                return;
            }
            int correctCount = 0;
            for (Map<String, Object> option : options) {
                if (intValue(option.get("isCorrect"), 0) == 1) {
                    correctCount++;
                }
            }
            if ((type == 0 || type == 2) && correctCount != 1) {
                issues.add(issue("INVALID_CORRECT_COUNT", "error",
                        "第 " + index + " 题必须且只能有一个正确选项。",
                        index,
                        "请只标记一个正确选项。"));
            }
            if (type == 1 && correctCount < 2) {
                issues.add(issue("INVALID_MULTI_CORRECT_COUNT", "error",
                        "第 " + index + " 题为多选题时，至少需要两个正确选项。",
                        index,
                        "请至少标记两个正确选项。"));
            }
            if (type == 2 && options.size() != 2) {
                issues.add(issue("INVALID_JUDGE_OPTION_COUNT", "error",
                        "第 " + index + " 题为判断题时，必须只有两个选项。",
                        index,
                        "请仅保留两个判断选项。"));
            }
            return;
        }
        if (options != null && !options.isEmpty()) {
            issues.add(issue("UNEXPECTED_OPTIONS", "warning",
                    "第 " + index + " 题为开放题，不应包含 options 列表。",
                    index,
                    "请清空 options 列表。"));
        }
        if (answers.isEmpty()) {
            issues.add(issue("MISSING_ANSWERS", "error", "第 " + index + " 题缺少答案。", index,
                    "开放题需要提供有效答案。"));
            return;
        }
        Set<Integer> sortOrders = new LinkedHashSet<>();
        for (Map<String, Object> answer : answers) {
            if (!StringUtils.hasText(value(answer.get("answerContent")))) {
                issues.add(issue("EMPTY_ANSWER", "error", "第 " + index + " 题存在空答案内容。", index,
                        "请为每个答案填写非空内容。"));
            }
            int sortOrder = intValue(answer.get("sortOrder"), 0);
            if (type == 3 && sortOrder <= 0) {
                issues.add(issue("INVALID_SORT_ORDER", "error",
                        "第 " + index + " 题填空答案的排序无效。",
                        index,
                        "请使用从 1 开始的正序排序值。"));
            } else if (type == 3 && !sortOrders.add(sortOrder)) {
                issues.add(issue("DUPLICATE_SORT_ORDER", "error",
                        "第 " + index + " 题填空答案的排序重复。",
                        index,
                        "请为每个空设置唯一排序值。"));
            }
        }
    }

    private void appendSectionTypeIssues(List<GenerationValidationIssue> issues,
                                         List<CreateQuestionRequest> questions,
                                         Integer expectedType) {
        if (expectedType == null || expectedType < 0 || expectedType > 4 || questions == null) {
            return;
        }
        for (int i = 0; i < questions.size(); i++) {
            int actualType = questions.get(i).questionType();
            if (actualType != expectedType) {
                issues.add(issue(
                        "SECTION_TYPE_MISMATCH",
                        "error",
                        "第 " + (i + 1) + " 题题型与当前部分要求不一致。",
                        i + 1,
                        "请按当前部分要求生成 " + questionTypeName(expectedType) + "。"));
            }
        }
    }

    private ReviewResult repairQuestions(List<CreateQuestionRequest> questions,
                                         GenerationRequest request,
                                         AiCourseContext context,
                                          PaperBlueprint blueprint,
                                          List<GenerationValidationIssue> issues,
                                          Set<String> referenceSignatures,
                                          List<GenerationTraceEntry> debugTraceEntries,
                                          Consumer<GenerationStageEvent> stageListener,
                                          String requestId,
                                          String mode,
                                          boolean exposeRawAiOutput,
                                          boolean paper) {
        List<CreateQuestionRequest> currentQuestions = deterministicRepair(questions, request);
        currentQuestions = rebalanceQuestionScores(currentQuestions, request);
        currentQuestions = rebalanceEstimatedTimes(currentQuestions, request.totalEstimatedTime());
        List<GenerationValidationIssue> currentIssues = validateQuestions(
                currentQuestions,
                request,
                request.questionCount(),
                request.totalScore(),
                request.totalEstimatedTime(),
                referenceSignatures);
        currentIssues = mergeIssues(currentIssues, issues);
        QuestionGenerationQualityPolicy.QualityReview currentQualityReview = qualityReview(currentQuestions, request, blueprint, paper);
        currentIssues = mergeIssues(currentIssues, currentQualityReview.issues());
        if (currentQuestions.isEmpty()) {
            appendTraceEntry(debugTraceEntries, "REPAIRED", "paperReview", "repair_summary",
                    "repair skipped",
                    "No usable draft questions were produced; returning EMPTY_RESULT without model repair.",
                    finalQuestionsPayload(currentQuestions, currentIssues));
            return new ReviewResult(currentQuestions, currentIssues);
        }
        if (!shouldRepairWithModel(currentQuestions, currentIssues, request.questionCount())) {
            appendTraceEntry(debugTraceEntries, "REPAIRED", "paperReview", "repair_summary",
                    "修复前复核",
                    "草稿题目已通过校验，无需额外修复。",
                    finalQuestionsPayload(currentQuestions, currentIssues));
            return new ReviewResult(currentQuestions, currentIssues);
        }

        List<CreateQuestionRequest> bestQuestions = currentQuestions;
        List<GenerationValidationIssue> bestIssues = currentIssues;
        for (int attemptNo = 1;
             attemptNo <= MAX_REPAIR_ATTEMPTS && shouldRepairWithModel(currentQuestions, currentIssues, request.questionCount());
             attemptNo++) {
            try {
                QuestionGeneratePayload repairedPayload = callRepairModel(
                        request, context, blueprint, questionMaps(currentQuestions), currentIssues, attemptNo,
                        debugTraceEntries, stageListener, requestId, mode, exposeRawAiOutput);
                NormalizedQuestionBatch repairedBatch = normalizeQuestionsFromRecords(
                        repairedPayload == null ? List.of() : repairedPayload.questionsOrEmpty(), request);
                List<CreateQuestionRequest> repairedQuestions = repairedBatch.questions();
                repairedQuestions = deterministicRepair(repairedQuestions, request);
                repairedQuestions = rebalanceQuestionScores(repairedQuestions, request);
                repairedQuestions = rebalanceEstimatedTimes(repairedQuestions, request.totalEstimatedTime());
                List<GenerationValidationIssue> repairedIssues = validateQuestions(
                        repairedQuestions,
                        request,
                        request.questionCount(),
                        request.totalScore(),
                        request.totalEstimatedTime(),
                        referenceSignatures);
                repairedIssues.addAll(repairedBatch.issues());
                repairedIssues = mergeIssues(repairedIssues, qualityReview(repairedQuestions, request, blueprint, paper).issues());
                appendTraceEntry(debugTraceEntries, "REPAIRED", "paperReview", "repair_attempt",
                        "第 " + attemptNo + " 轮修复",
                        "本轮产出 " + repairedQuestions.size() + " 道题目，仍有 "
                                + repairedIssues.size() + " 个问题待处理。",
                        repairAttemptPayload(attemptNo, repairedQuestions, repairedIssues));
                if (isBetterCandidate(repairedQuestions, repairedIssues, bestQuestions, bestIssues, request.questionCount())) {
                    bestQuestions = repairedQuestions;
                    bestIssues = repairedIssues;
                }
                currentQuestions = repairedQuestions;
                currentIssues = repairedIssues;
            } catch (RuntimeException exception) {
                appendTraceEntry(debugTraceEntries, "REPAIRED", "paperReview", "repair_attempt",
                        "第 " + attemptNo + " 轮修复",
                        "本轮修复未产出可用结果，已保留当前最优版本。",
                        repairAttemptPayload(attemptNo, List.of(), currentIssues));
                break;
            }
        }
        appendTraceEntry(debugTraceEntries, "REPAIRED", "paperReview", "repair_summary",
                "修复完成",
                "已选择当前最佳修复结果，共 " + bestQuestions.size() + " 道题目，剩余 "
                        + bestIssues.size() + " 个问题。",
                finalQuestionsPayload(bestQuestions, bestIssues));
        QuestionGenerationQualityPolicy.QualityReview finalQualityReview = qualityReview(bestQuestions, request, blueprint, paper);
        appendTraceEntry(debugTraceEntries, "REPAIRED", "qualityReview", "quality_review",
                "修复后质量复核",
                finalQualityReview.issues().isEmpty()
                        ? "修复后未发现明显质量问题。"
                        : "修复后仍有 " + finalQualityReview.issues().size() + " 个质量提示。",
                finalQualityReview.payload());
        return new ReviewResult(bestQuestions, bestIssues);
    }

    private boolean shouldRepairWithModel(List<CreateQuestionRequest> questions,
                                          List<GenerationValidationIssue> issues,
                                          Integer expectedCount) {
        if (questions == null || questions.isEmpty()) {
            return true;
        }
        if (expectedCount != null && questions.size() != expectedCount) {
            return true;
        }
        return hasBlockingIssues(issues)
                || issues.stream().anyMatch(QuestionGenerationQualityPolicy::isRepairable);
    }

    private ReviewResult mergeGenerationWarnings(ReviewResult result, List<GenerationValidationIssue> draftIssues) {
        if (draftIssues == null || draftIssues.isEmpty()) {
            return result;
        }
        List<GenerationValidationIssue> merged = new ArrayList<>(result.issues());
        Set<String> existing = issueKeys(merged);
        for (GenerationValidationIssue issue : draftIssues) {
            if (issue == null || !"warning".equalsIgnoreCase(issue.level())) {
                continue;
            }
            String key = issueKey(issue);
            if (existing.add(key)) {
                merged.add(issue);
            }
        }
        return new ReviewResult(result.questions(), merged);
    }

    private Set<String> issueKeys(List<GenerationValidationIssue> issues) {
        Set<String> keys = new LinkedHashSet<>();
        if (issues == null) {
            return keys;
        }
        for (GenerationValidationIssue issue : issues) {
            if (issue != null) {
                keys.add(issueKey(issue));
            }
        }
        return keys;
    }

    private String issueKey(GenerationValidationIssue issue) {
        return value(issue.code()) + "|" + value(issue.message()) + "|" + value(issue.questionIndex());
    }

    private QuestionGeneratePayload callRepairModel(GenerationRequest request,
                                                    AiCourseContext context,
                                                     PaperBlueprint blueprint,
                                                     List<Map<String, Object>> currentQuestions,
                                                     List<GenerationValidationIssue> issues,
                                                     int attemptNo,
                                                     List<GenerationTraceEntry> debugTraceEntries,
                                                     Consumer<GenerationStageEvent> stageListener,
                                                     String requestId,
                                                     String mode,
                                                     boolean exposeRawAiOutput) {
        String prompt = """
                你是教育测评题目修复助手。当前任务：修复已经生成的题目集合。
                只返回合法 JSON，不要使用 markdown 代码块，不要输出解释。
                顶层结构必须是：{"questions":[...]}。
                questions 数组必须包含 exactly %s 道题。
                修复结构问题时尽量保留原始教学意图。
                每道题只输出模型字段：questionTitle, questionContent, questionType, difficulty, score, estimatedTime, tags, options, answers。
                不要生成 questionBankId、id、questionId、allowPartialCredit 或任何业务标识字段。
                score/estimatedTime 必须为正数。
                如果存在重复题、题数不符、选项/答案结构不合法、总分或总时长不匹配，请全部修复。
                %s
                %s

                修复尝试：%s / %s
                原始生成参数 JSON：%s
                蓝图 JSON：%s
                当前题目 JSON：%s
                校验问题 JSON：%s
                平台课程资料摘要：%s
                """.formatted(
                request.questionCount(),
                QUESTION_OUTPUT_RULES.stripTrailing(),
                LatexPromptRules.TEXT.stripTrailing(),
                attemptNo,
                MAX_REPAIR_ATTEMPTS,
                toJson(requestPayload(request)),
                toJson(blueprintPayload(blueprint)),
                toJson(currentQuestions),
                toJson(issues),
                platformDataTool.summarize(context));
        String rawOutput = aiProviderCallGuard.call(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());
        publishRawAiOutput(debugTraceEntries, stageListener, requestId, mode, exposeRawAiOutput,
                "REPAIRED",
                "第 " + attemptNo + " 轮修复模型输出",
                "模型已返回题目修复的原始内容。",
                rawAiOutputPayload("repair_generation", null, attemptNo, rawOutput));
        return parseQuestionGeneratePayload(rawOutput);
    }

    private List<CreateQuestionRequest> deterministicRepair(List<CreateQuestionRequest> questions, GenerationRequest request) {
        return createQuestionRequests(deterministicRepairMaps(questionMaps(questions), request));
    }

    private List<Map<String, Object>> deterministicRepairMaps(List<Map<String, Object>> questions, GenerationRequest request) {
        List<Map<String, Object>> repaired = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> copy = new LinkedHashMap<>(questions.get(i));
            if (request.questionBankId() != null) {
                copy.put("questionBankId", request.questionBankId().toString());
            }
            copy.put("questionType", normalizeQuestionType(intValue(copy.get("questionType"), request.questionType()), 4));
            copy.put("difficulty", normalizeDifficulty(intValue(copy.get("difficulty"), request.difficulty()), 2));
            copy.put("score", decimalValue(copy.get("score"), scorePerQuestion(request)));
            copy.put("estimatedTime", Math.max(1, intValue(copy.get("estimatedTime"),
                    recommendEstimatedTime(intValue(copy.get("questionType"), 4), intValue(copy.get("difficulty"), 2)))));
            normalizeQuestionTextFields(copy, request, i);
            normalizeQuestionStructure(copy, request);
            normalizeLatexTextFields(copy);
            repaired.add(copy);
        }
        return repaired;
    }

    private void normalizeLatexTextFields(Map<String, Object> question) {
        question.put("questionContent", wrapStandaloneBareLatex(value(question.get("questionContent"))));
        List<Map<String, Object>> options = mutableMapList(question.get("options"));
        for (Map<String, Object> option : options) {
            option.put("optionContent", wrapStandaloneBareLatex(value(option.get("optionContent"))));
            option.put("explanation", wrapStandaloneBareLatex(value(option.get("explanation"))));
        }
        question.put("options", options);
        List<Map<String, Object>> answers = mutableMapList(question.get("answers"));
        for (Map<String, Object> answer : answers) {
            answer.put("answerContent", wrapStandaloneBareLatex(value(answer.get("answerContent"))));
            answer.put("explanation", wrapStandaloneBareLatex(value(answer.get("explanation"))));
        }
        question.put("answers", answers);
    }

    private String wrapStandaloneBareLatex(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.strip();
        if (MATH_DELIMITER_PATTERN.matcher(trimmed).find()
                || !LATEX_COMMAND_PATTERN.matcher(trimmed).find()
                || CJK_TEXT_PATTERN.matcher(trimmed).find()
                || trimmed.contains("\\begin{")
                || trimmed.contains("\\end{")
                || LATEX_ROW_SEPARATOR_PATTERN.matcher(trimmed).find()
                || containsPlainProse(trimmed)) {
            return trimmed;
        }
        return "$" + trimmed + "$";
    }

    private boolean containsPlainProse(String text) {
        String withoutLatexCommands = LATEX_COMMAND_NAME_PATTERN.matcher(
                LATEX_TEXT_COMMAND_PATTERN.matcher(text).replaceAll(" ")).replaceAll(" ");
        return LATIN_WORD_PATTERN.matcher(withoutLatexCommands).find();
    }

    private List<CreateQuestionRequest> rebalanceQuestionScores(List<CreateQuestionRequest> questions, GenerationRequest request) {
        List<Map<String, Object>> maps = questionMaps(questions);
        rebalanceQuestionScoreMaps(maps, request);
        return createQuestionRequests(maps);
    }

    private void rebalanceQuestionScoreMaps(List<Map<String, Object>> questions, GenerationRequest request) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        BigDecimal requestedScorePerQuestion = normalizePositiveScore(request.scorePerQuestion());
        BigDecimal requestedTotalScore = normalizePositiveScore(request.totalScore());
        if (requestedScorePerQuestion != null && requestedTotalScore == null) {
            for (Map<String, Object> question : questions) {
                question.put("score", requestedScorePerQuestion);
                synchronizeNestedScore(question);
            }
            return;
        }
        if (requestedTotalScore == null) {
            questions.forEach(this::synchronizeNestedScore);
            return;
        }
        if (requestedScorePerQuestion != null
                && requestedScorePerQuestion.multiply(BigDecimal.valueOf(questions.size())).compareTo(requestedTotalScore) == 0) {
            for (Map<String, Object> question : questions) {
                question.put("score", requestedScorePerQuestion);
                synchronizeNestedScore(question);
            }
            return;
        }
        List<Long> weights = questions.stream()
                .map(question -> {
                    BigDecimal score = normalizePositiveScore(decimalValue(question.get("score"), null));
                    return scorePerQuestionSpecifiedAsZero(request) && score != null ? toWeight(score) : QuestionGenerationQualityPolicy.scoreWeight(
                            intValue(question.get("questionType"), 4),
                            intValue(question.get("difficulty"), 2));
                })
                .toList();
        List<BigDecimal> scores = scorePerQuestionSpecifiedAsZero(request)
                ? allocateWeightedScores(weights, requestedTotalScore, 0L)
                : allocateWeightedScores(weights, requestedTotalScore);
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).put("score", scores.get(i));
            synchronizeNestedScore(questions.get(i));
        }
    }

    private List<CreateQuestionRequest> rebalanceEstimatedTimes(List<CreateQuestionRequest> questions, Integer targetTotalTime) {
        List<Map<String, Object>> maps = questionMaps(questions);
        rebalanceEstimatedTimeMaps(maps, targetTotalTime);
        return createQuestionRequests(maps);
    }

    private void rebalanceEstimatedTimeMaps(List<Map<String, Object>> questions, Integer targetTotalTime) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        List<Integer> weights = new ArrayList<>(questions.size());
        for (Map<String, Object> question : questions) {
            int suggested = recommendEstimatedTime(
                    intValue(question.get("questionType"), 4),
                    intValue(question.get("difficulty"), 2));
            int current = intValue(question.get("estimatedTime"), suggested);
            question.put("estimatedTime", Math.max(1, current));
            weights.add(Math.max(1, targetTotalTime == null || targetTotalTime <= 0 ? current : suggested));
        }
        if (targetTotalTime == null || targetTotalTime <= 0) {
            return;
        }
        List<Integer> minimums = questions.stream().map(ignored -> 1).toList();
        List<Integer> times = allocateWeightedIntegers(weights, targetTotalTime, minimums);
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).put("estimatedTime", times.get(i));
        }
    }

    private void synchronizeNestedScore(Map<String, Object> question) {
        int type = intValue(question.get("questionType"), 4);
        if (type <= 2) {
            synchronizeOptionScores(question);
            return;
        }
        synchronizeAnswerScores(question);
    }

    private List<CreateQuestionRequest> synchronizeFinalNestedScores(List<CreateQuestionRequest> questions) {
        List<Map<String, Object>> maps = questionMaps(questions);
        maps.forEach(this::synchronizeNestedScore);
        return createQuestionRequests(maps);
    }

    private void synchronizeOptionScores(Map<String, Object> question) {
        List<Map<String, Object>> options = mutableMapList(question.get("options"));
        if (options.isEmpty()) {
            return;
        }
        BigDecimal questionScore = decimalValue(question.get("score"), BigDecimal.ZERO);
        List<Map<String, Object>> correctOptions = options.stream()
                .filter(option -> intValue(option.get("isCorrect"), 0) == 1)
                .toList();
        if (correctOptions.isEmpty()) {
            return;
        }
        if (intValue(question.get("questionType"), 0) != 1 || correctOptions.size() == 1) {
            for (Map<String, Object> option : options) {
                option.put("score", intValue(option.get("isCorrect"), 0) == 1 ? questionScore : BigDecimal.ZERO);
            }
            question.put("options", options);
            return;
        }
        List<Long> weights = correctOptions.stream()
                .map(option -> Math.max(1L, toWeight(decimalValue(option.get("score"), BigDecimal.ONE))))
                .toList();
        List<BigDecimal> distributed = allocateWeightedScores(weights, questionScore);
        int correctIndex = 0;
        for (Map<String, Object> option : options) {
            if (intValue(option.get("isCorrect"), 0) == 1) {
                option.put("score", distributed.get(correctIndex++));
            } else {
                option.put("score", BigDecimal.ZERO);
            }
        }
        question.put("options", options);
    }

    private void synchronizeAnswerScores(Map<String, Object> question) {
        List<Map<String, Object>> answers = mutableMapList(question.get("answers"));
        if (answers.isEmpty()) {
            return;
        }
        BigDecimal questionScore = decimalValue(question.get("score"), BigDecimal.ZERO);
        List<Long> weights = answers.stream()
                .map(answer -> Math.max(1L, toWeight(decimalValue(answer.get("score"), BigDecimal.ONE))))
                .toList();
        List<BigDecimal> distributed = allocateWeightedScores(weights, questionScore);
        for (int i = 0; i < answers.size(); i++) {
            answers.get(i).put("score", distributed.get(i));
        }
        question.put("answers", answers);
    }

    private Map<String, Object> finalPayload(Map<String, Object> modelPayload,
                                              GenerationRequest request,
                                              PaperBlueprint blueprint,
                                              List<CreateQuestionRequest> finalQuestions,
                                              List<GenerationValidationIssue> issues,
                                              List<GenerationTraceEntry> traceEntries,
                                              List<GenerationTraceEntry> debugTraceEntries,
                                              List<AgentSearchEvent> agentSearchEvents,
                                              boolean paper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", text(modelPayload, "title", blueprint.title()));
        payload.put("type", paper ? "paper" : "question_set");
        payload.put("schemaVersion", 2);
        payload.put("generation", requestPayload(request));
        payload.put("blueprint", blueprint);
        payload.put("questions", questionMaps(finalQuestions));
        payload.put("issues", issues);
        payload.put("generationTrace", traceEntries);
        payload.put("generationDebugTrace", debugTraceEntries);
        payload.put("agentSearch", agentSearchPayload(agentSearchEvents));
        if (modelPayload.get("raw") != null) {
            payload.put("raw", modelPayload.get("raw"));
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private String formatQuestions(Map<String, Object> payload, boolean paper) {
        StringBuilder builder = new StringBuilder();
        builder.append(paper ? "## AI 出卷结果\n\n" : "## AI 出题结果\n\n");
        Object title = payload.get("title");
        if (title != null) {
            builder.append("**").append(title).append("**\n\n");
        }
        Object questionsValue = payload.get("questions");
        if (questionsValue instanceof List<?> questions && !questions.isEmpty()) {
            int index = 1;
            for (Object item : questions) {
                if (item instanceof Map<?, ?> question) {
                    builder.append(index++).append(". ")
                            .append(value(question.get("questionTitle")))
                            .append("\n");
                    appendIfPresent(builder, question, "questionContent", "   ");
                    appendList(builder, (List<Object>) question.get("options"), "   - ");
                    appendList(builder, (List<Object>) question.get("answers"), "   答案: ");
                    builder.append('\n');
                }
            }
            return builder.toString();
        }
        if (payload.get("raw") != null) {
            return builder.append(payload.get("raw")).toString();
        }
        return builder.append("未生成可用题目。").toString();
    }

    private void appendIfPresent(StringBuilder builder, Map<?, ?> map, String key, String prefix) {
        Object value = map.get(key);
        if (value != null && !value.toString().isBlank()) {
            builder.append(prefix).append(value).append('\n');
        }
    }

    private void appendList(StringBuilder builder, List<Object> values, String prefix) {
        if (values == null) {
            return;
        }
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                Object label = map.get("optionLabel");
                if (label == null) {
                    label = map.get("label");
                }
                Object content = map.get("optionContent");
                if (content == null) {
                    content = map.get("content");
                }
                if (content == null) {
                    content = map.get("answerContent");
                }
                builder.append(prefix);
                if (label != null) {
                    builder.append(label).append(". ");
                }
                builder.append(content != null ? content : map).append('\n');
            }
        }
    }

    private void emitStage(Consumer<GenerationStageEvent> listener,
                           List<GenerationTraceEntry> traceEntries,
                           String requestId,
                           String mode,
                           String stage,
                           String status,
                           String title,
                           String summary,
                           Map<String, Object> payload,
                           String detailType) {
        Map<String, Object> eventPayload = withDetailType(payload, detailType);
        GenerationStageEvent event = GenerationStageEvent.of(requestId, mode, stage, status, title, summary, eventPayload);
        traceEntries.add(new GenerationTraceEntry(
                UuidV7Generator.generate().toString(),
                stage,
                "question-generation",
                detailType,
                title,
                summary,
                event.payload(),
                event.timestamp()));
        if (listener != null) {
            listener.accept(event);
        }
    }

    private Map<String, Object> withDetailType(Map<String, Object> payload, String detailType) {
        Map<String, Object> next = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        if (detailType != null && !detailType.isBlank()) {
            next.putIfAbsent("detailType", detailType);
        }
        return next;
    }

    private void appendTraceEntry(List<GenerationTraceEntry> traceEntries,
                                  String stage,
                                  String source,
                                  String detailType,
                                  String title,
                                  String summary,
                                  Map<String, Object> payload) {
        traceEntries.add(new GenerationTraceEntry(
                UuidV7Generator.generate().toString(),
                stage,
                source,
                detailType,
                title,
                summary,
                payload == null ? Map.of() : payload,
                Instant.now()));
    }

    private void publishRawAiOutput(List<GenerationTraceEntry> debugTraceEntries,
                                    Consumer<GenerationStageEvent> stageListener,
                                    String requestId,
                                    String mode,
                                    boolean exposeRawAiOutput,
                                    String stage,
                                    String title,
                                    String summary,
                                    Map<String, Object> payload) {
        if (!exposeRawAiOutput) {
            return;
        }
        appendTraceEntry(debugTraceEntries, stage, "ai-provider", "raw_ai_output", title, summary, payload);
        if (stageListener != null) {
            stageListener.accept(GenerationStageEvent.of(requestId, mode, stage, "processing", title, summary, payload));
        }
    }

    private Map<String, Object> rawAiOutputPayload(String callType, Integer sectionNo, int attemptNo, String rawOutput) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("detailType", "raw_ai_output");
        payload.put("callType", callType);
        payload.put("attemptNo", attemptNo);
        if (sectionNo != null) {
            payload.put("sectionNo", sectionNo);
        }
        payload.put("rawOutput", rawOutput == null ? "" : rawOutput);
        return payload;
    }

    private QuestionGeneratePayload parseQuestionGeneratePayload(String rawOutput) {
        try {
            return objectMapper.readValue(extractJsonObject(rawOutput), QuestionGeneratePayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to parse question generation model output", exception);
        }
    }

    private String extractJsonObject(String rawOutput) throws JsonParseException {
        if (!StringUtils.hasText(rawOutput)) {
            throw new JsonParseException(null, "Empty question generation model output");
        }
        String text = rawOutput.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            throw new JsonParseException(null, "Question generation model output does not contain a JSON object");
        }
        return text.substring(start, end + 1);
    }

    private Map<String, Object> requestPayload(GenerationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionBankId", request.questionBankId());
        payload.put("questionCount", request.questionCount());
        payload.put("questionType", request.questionType());
        payload.put("difficulty", request.difficulty());
        payload.put("scorePerQuestion", request.scorePerQuestion());
        payload.put("totalScore", request.totalScore());
        payload.put("totalEstimatedTime", request.totalEstimatedTime());
        payload.put("paperName", request.paperName());
        payload.put("paperType", request.paperType());
        payload.put("requirement", request.requirement());
        payload.put("chapterIds", safeList(request.chapterIds()));
        payload.put("knowledgePoints", safeList(request.knowledgePoints()));
        payload.put("abilityGoals", safeList(request.abilityGoals()));
        return payload;
    }

    private Map<String, Object> evidencePayload(List<AgentSearchItem> evidences, List<AgentSearchEvent> events) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evidenceCount", evidences.size());
        payload.put("evidences", summarizeEvidences(evidences, 6));
        payload.put("webSources", webSourcePayload(evidences));
        payload.put("agentSearch", agentSearchPayload(events));
        return payload;
    }

    private Map<String, Object> blueprintPayload(PaperBlueprint blueprint) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("blueprintId", blueprint.blueprintId());
        payload.put("generationStrategy", blueprint.generationStrategy());
        payload.put("totalQuestionCount", blueprint.totalQuestionCount());
        payload.put("totalScore", blueprint.totalScore());
        payload.put("totalEstimatedTime", blueprint.totalEstimatedTime());
        payload.put("sectionCount", blueprint.sections().size());
        payload.put("sections", blueprint.sections());
        return payload;
    }

    private Map<String, Object> blueprintSummaryPayload(PaperBlueprint blueprint) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("blueprintId", blueprint.blueprintId());
        payload.put("totalQuestionCount", blueprint.totalQuestionCount());
        payload.put("totalScore", blueprint.totalScore());
        payload.put("totalEstimatedTime", blueprint.totalEstimatedTime());
        payload.put("sectionCount", blueprint.sections().size());
        return payload;
    }

    private Map<String, Object> draftPayload(List<GeneratedQuestionDraft> drafts) {
        return Map.of(
                "questionCount", drafts.size(),
                "drafts", draftMaps(drafts),
                "questions", questionMaps(drafts.stream().map(GeneratedQuestionDraft::question).toList()));
    }

    private void publishDraftProgress(Consumer<GenerationStageEvent> listener,
                                      List<GenerationTraceEntry> traceEntries,
                                      String requestId,
                                      String mode,
                                      PaperSectionPlan section,
                                      List<CreateQuestionRequest> allQuestions,
                                      List<CreateQuestionRequest> latestQuestions,
                                      List<GenerationValidationIssue> latestIssues,
                                      int totalQuestionCount) {
        if ((latestQuestions == null || latestQuestions.isEmpty()) && !allQuestions.isEmpty()) {
            return;
        }
        emitStage(listener, traceEntries, requestId, mode, "GENERATED", "processing",
                "题目草稿生成中",
                "已生成 " + allQuestions.size() + " / " + totalQuestionCount + " 道题目草稿。",
                draftedQuestionsPayload(section, allQuestions, latestQuestions, latestIssues, totalQuestionCount),
                "draft_progress");
    }

    private void publishDraftProgressByQuestion(Consumer<GenerationStageEvent> listener,
                                                List<GenerationTraceEntry> traceEntries,
                                                String requestId,
                                                String mode,
                                                PaperSectionPlan section,
                                                List<CreateQuestionRequest> latestQuestions,
                                                List<GenerationValidationIssue> latestIssues,
                                                int totalQuestionCount,
                                                int previousQuestionCount) {
        if (latestQuestions == null || latestQuestions.isEmpty()) {
            return;
        }
        for (int i = 0; i < latestQuestions.size(); i++) {
            int generatedQuestionCount = previousQuestionCount + i + 1;
            List<GenerationValidationIssue> issues = i == latestQuestions.size() - 1
                    ? latestIssues
                    : List.of();
            emitStage(listener, traceEntries, requestId, mode, "GENERATED", "processing",
                    "题目草稿生成中",
                    "已生成 " + generatedQuestionCount + " / " + totalQuestionCount + " 道题目草稿。",
                    draftedQuestionDeltaPayload(section, latestQuestions.get(i), issues,
                            generatedQuestionCount, totalQuestionCount),
                    "draft_progress");
        }
    }

    private Map<String, Object> draftedQuestionsPayload(PaperSectionPlan section,
                                                        List<CreateQuestionRequest> allQuestions,
                                                        List<CreateQuestionRequest> latestQuestions,
                                                        List<GenerationValidationIssue> latestIssues,
                                                        int totalQuestionCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (section != null) {
            payload.put("sectionNo", section.sectionNo());
        }
        payload.put("questionCount", latestQuestions.size());
        payload.put("generatedQuestionCount", allQuestions.size());
        payload.put("totalQuestionCount", totalQuestionCount);
        payload.put("questionDelta", true);
        payload.put("questions", questionMaps(latestQuestions));
        payload.put("issues", latestIssues == null ? List.of() : latestIssues);
        return payload;
    }

    private Map<String, Object> draftedQuestionDeltaPayload(PaperSectionPlan section,
                                                            CreateQuestionRequest question,
                                                            List<GenerationValidationIssue> latestIssues,
                                                            int generatedQuestionCount,
                                                            int totalQuestionCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (section != null) {
            payload.put("sectionNo", section.sectionNo());
        }
        payload.put("questionCount", 1);
        payload.put("generatedQuestionCount", generatedQuestionCount);
        payload.put("totalQuestionCount", totalQuestionCount);
        payload.put("questionDelta", true);
        payload.put("questions", questionMaps(List.of(question)));
        payload.put("issues", latestIssues == null ? List.of() : latestIssues);
        return payload;
    }

    private List<Map<String, Object>> draftMaps(List<GeneratedQuestionDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (GeneratedQuestionDraft draft : drafts) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("draftId", draft.draftId());
            map.put("order", draft.order());
            map.put("question", questionMap(draft.question()));
            map.put("issues", draft.issues());
            values.add(map);
        }
        return values;
    }

    private Map<String, Object> validationPayload(List<CreateQuestionRequest> questions, List<GenerationValidationIssue> issues) {
        return Map.of(
                "questionCount", questions.size(),
                "issueCount", issues.size(),
                "questions", summarizeQuestions(questions, 6),
                "issues", issues);
    }

    private Map<String, Object> questionPayload(List<CreateQuestionRequest> questions, List<GenerationValidationIssue> issues) {
        return Map.of(
                "questionCount", questions.size(),
                "remainingIssueCount", issues.size(),
                "questions", questionMaps(questions),
                "issues", issues);
    }

        private Map<String, Object> sectionAttemptPayload(PaperSectionPlan section,
                                                      int attemptNo,
                                                      List<CreateQuestionRequest> questions,
                                                      List<GenerationValidationIssue> issues,
                                                      int promptEvidenceCount,
                                                      int discardedEvidenceCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("section", section);
        payload.put("attemptNo", attemptNo);
        payload.put("questionCount", questions.size());
        payload.put("questions", questionMaps(questions));
        payload.put("issues", issues);
        payload.put("promptEvidenceCount", promptEvidenceCount);
        payload.put("discardedEvidenceCount", discardedEvidenceCount);
        return payload;
    }

    private Map<String, Object> repairAttemptPayload(int attemptNo,
                                                     List<CreateQuestionRequest> questions,
                                                     List<GenerationValidationIssue> issues) {
        return Map.of(
                "attemptNo", attemptNo,
                "questionCount", questions.size(),
                "questions", questionMaps(questions),
                "issues", issues);
    }

    private Map<String, Object> finalQuestionsPayload(List<CreateQuestionRequest> questions,
                                                      List<GenerationValidationIssue> issues) {
        return Map.of(
                "questionCount", questions.size(),
                "questions", questionMaps(questions),
                "issues", issues);
    }

    private Map<String, Object> agentSearchPayload(List<AgentSearchEvent> events) {
        List<AgentSearchEvent> safeEvents = events == null ? List.of() : events;
        return Map.of(
                "events", safeEvents,
                "searches", agentSearches(safeEvents),
                "items", agentSearchItems(safeEvents));
    }

    private List<Map<String, Object>> agentSearches(List<AgentSearchEvent> events) {
        Map<String, Map<String, Object>> searches = new LinkedHashMap<>();
        for (AgentSearchEvent event : events) {
            if (event == null || "completed".equals(event.phase())) {
                continue;
            }
            String key = StringUtils.hasText(event.searchId()) ? event.searchId() : event.domain() + ":" + event.query();
            Map<String, Object> search = searches.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            search.putIfAbsent("searchId", event.searchId());
            search.putIfAbsent("domain", event.domain());
            search.putIfAbsent("query", event.query());
            search.put("label", event.label());
            search.put("phase", event.phase());
            search.put("total", event.total());
            search.put("occurredAt", event.occurredAt());
            putIfNotNull(search, "status", event.status());
            putIfNotNull(search, "reason", event.reason());
            putIfNotNull(search, "provider", event.provider());
            putIfNotNull(search, "durationMs", event.durationMs());
            putIfNotNull(search, "retryable", event.retryable());
            if (event.items() != null && !event.items().isEmpty()) {
                search.put("items", event.items());
            }
        }
        return List.copyOf(searches.values());
    }

    private List<Object> agentSearchItems(List<AgentSearchEvent> events) {
        return events.stream()
                .filter(event -> event.items() != null)
                .flatMap(event -> event.items().stream())
                .map(item -> (Object) item)
                .toList();
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private List<Map<String, Object>> summarizeEvidences(List<AgentSearchItem> evidences, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (evidences == null) {
            return items;
        }
        for (AgentSearchItem evidence : evidences) {
            if (evidence == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceType", evidence.sourceType());
            item.put("sourceId", evidence.sourceId());
            item.put("title", evidence.title());
            item.put("excerpt", abbreviate(evidence.snippet(), 180));
            item.put("metadata", evidence.metadata());
            items.add(item);
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private List<Map<String, Object>> summarizePromptEvidences(List<AgentSearchItem> evidences) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (evidences == null || evidences.isEmpty()) {
            return items;
        }
        for (AgentSearchItem evidence : evidences) {
            if (evidence == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceType", evidence.sourceType());
            item.put("sourceId", evidence.sourceId());
            item.put("title", evidence.title());
            item.put("contextLabel", evidence.contextLabel());
            item.put("excerpt", abbreviate(evidence.snippet(), PROMPT_EVIDENCE_EXCERPT_MAX_LENGTH));
            items.add(item);
            if (items.size() >= PROMPT_EVIDENCE_LIMIT) {
                break;
            }
        }
        return items;
    }

    private List<Map<String, Object>> webSourcePayload(List<AgentSearchItem> evidences) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (evidences == null || evidences.isEmpty()) {
            return items;
        }
        for (AgentSearchItem evidence : evidences) {
            if (evidence == null || !"WEB_SEARCH".equalsIgnoreCase(value(evidence.sourceType()))) {
                continue;
            }
            String url = text(evidence.metadata(), "url", evidence.sourceId());
            if (!StringUtils.hasText(url)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("site", webDomain(url));
            item.put("title", evidence.title());
            item.put("url", url);
            item.put("favicon", text(evidence.metadata(), "favicon", ""));
            item.put("snippet", abbreviate(evidence.snippet(), 180));
            items.add(item);
        }
        return items;
    }

    private String webDomain(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (!StringUtils.hasText(host)) {
                return url;
            }
            return host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    private List<Map<String, Object>> summarizeQuestions(List<CreateQuestionRequest> questions, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (questions == null) {
            return items;
        }
        for (Map<String, Object> question : questionMaps(questions)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionBankId", question.get("questionBankId"));
            item.put("questionTitle", abbreviate(value(question.get("questionTitle")), 120));
            item.put("questionContent", abbreviate(value(question.get("questionContent")), 180));
            item.put("questionType", question.get("questionType"));
            item.put("difficulty", question.get("difficulty"));
            item.put("score", question.get("score"));
            item.put("estimatedTime", question.get("estimatedTime"));
            item.put("tags", question.get("tags"));
            item.put("allowPartialCredit", question.get("allowPartialCredit"));
            items.add(item);
            if (items.size() >= limit) {
                break;
            }
        }
        return items;
    }

    private List<String> knowledgePoints(GenerationRequest request,
                                         AiCourseContext context,
                                         List<AgentSearchItem> evidences) {
        if (request.knowledgePoints() != null && !request.knowledgePoints().isEmpty()) {
            return request.knowledgePoints();
        }
        if (context != null && context.chapters() != null && !context.chapters().isEmpty()) {
            return context.chapters().stream()
                    .map(AiCourseContext.ChapterSummary::chapterName)
                    .filter(StringUtils::hasText)
                    .limit(5)
                    .toList();
        }
        return evidences.stream()
                .map(AgentSearchItem::title)
                .filter(StringUtils::hasText)
                .limit(5)
                .toList();
    }

    private GenerationRequest sectionRequest(GenerationRequest request, PaperSectionPlan section) {
        return new GenerationRequest(
                request.questionBankId(),
                section.targetCount(),
                section.questionType(),
                section.difficulty(),
                section.scorePerQuestion(),
                null,
                section.totalEstimatedTime(),
                request.paperName(),
                request.paperType(),
                request.requirement(),
                request.chapterIds(),
                section.knowledgePoints(),
                request.abilityGoals());
    }

    private BigDecimal scorePerQuestion(GenerationRequest request) {
        if (request.scorePerQuestion() != null && request.scorePerQuestion().compareTo(BigDecimal.ZERO) > 0) {
            return request.scorePerQuestion();
        }
        if (request.totalScore() != null && request.questionCount() != null && request.questionCount() > 0) {
            return request.totalScore().divide(BigDecimal.valueOf(request.questionCount()), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(5);
    }

    private boolean scorePerQuestionSpecifiedAsZero(GenerationRequest request) {
        return request.scorePerQuestion() != null && request.scorePerQuestion().compareTo(BigDecimal.ZERO) == 0;
    }

    private int recommendEstimatedTime(Integer questionType, Integer difficulty) {
        int normalizedDifficulty = normalizeDifficulty(difficulty, 2);
        return switch (questionType != null ? questionType : -1) {
            case 2 -> switch (normalizedDifficulty) {
                case 1 -> 1;
                case 2 -> 2;
                default -> 3;
            };
            case 0 -> switch (normalizedDifficulty) {
                case 1 -> 2;
                case 2 -> 3;
                default -> 4;
            };
            case 1, 3 -> switch (normalizedDifficulty) {
                case 1 -> 3;
                case 2 -> 4;
                default -> 6;
            };
            case 4 -> switch (normalizedDifficulty) {
                case 1 -> 5;
                case 2 -> 7;
                default -> 10;
            };
            default -> switch (normalizedDifficulty) {
                case 1 -> 2;
                case 2 -> 3;
                default -> 5;
            };
        };
    }

    private List<String> normalizeStringList(Object value, List<String> fallback) {
        if (value instanceof Collection<?> collection) {
            List<String> result = collection.stream()
                    .filter(item -> item != null && StringUtils.hasText(item.toString()))
                    .map(item -> item.toString().strip())
                    .toList();
            return result.isEmpty() ? safeList(fallback) : result;
        }
        return safeList(fallback);
    }

    private <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private String text(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null || !StringUtils.hasText(value.toString()) ? fallback : value.toString().strip();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null && StringUtils.hasText(value.toString())) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value != null && StringUtils.hasText(value.toString())) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Object questionBankIdValue(Map<?, ?> source, GenerationRequest request) {
        if (request.questionBankId() != null) {
            return request.questionBankId().toString();
        }
        String sourceValue = text(source, "questionBankId", "");
        return StringUtils.hasText(sourceValue) ? sourceValue : null;
    }

    private int normalizeQuestionType(Integer value, int fallback) {
        int normalized = value == null ? fallback : value;
        if (normalized < 0 || normalized > 4) {
            return fallback < 0 || fallback > 4 ? 4 : fallback;
        }
        return normalized;
    }

    private int normalizeDifficulty(Integer value, int fallback) {
        int normalized = value == null ? fallback : value;
        if (normalized < 1 || normalized > 3) {
            return fallback < 1 || fallback > 3 ? 2 : fallback;
        }
        return normalized;
    }

    private int normalizeFlag(int value) {
        return value == 0 ? 0 : 1;
    }

    private int normalizeCorrectFlag(Object isCorrect) {
        if (isCorrect instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (isCorrect != null && StringUtils.hasText(isCorrect.toString())) {
            return normalizeFlag(intValue(isCorrect, 0));
        }
        return 0;
    }

    private String defaultAnswerContent(Map<String, Object> question) {
        List<Map<String, Object>> options = mutableMapList(question.get("options"));
        List<String> correctOptions = options.stream()
                .filter(option -> intValue(option.get("isCorrect"), 0) == 1)
                .map(option -> {
                    String label = value(option.get("optionLabel"));
                    String content = value(option.get("optionContent"));
                    return StringUtils.hasText(label) && StringUtils.hasText(content) ? label + ". " + content : content;
                })
                .filter(StringUtils::hasText)
                .toList();
        if (!correctOptions.isEmpty()) {
            return String.join("，", correctOptions);
        }
        return "请教师根据题干补充参考答案。";
    }

    private String questionTypeName(Integer type) {
        return switch (type == null ? 5 : type) {
            case 0 -> "单选题";
            case 1 -> "多选题";
            case 2 -> "判断题";
            case 3 -> "填空题";
            case 4 -> "简答题";
            default -> "混合题";
        };
    }

    private GenerationValidationIssue issue(String code, String level, String message, Integer questionIndex, String repairHint) {
        return new GenerationValidationIssue(code, level, message, questionIndex, repairHint);
    }

    private boolean hasBlockingIssues(List<GenerationValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return false;
        }
        return issues.stream().anyMatch(issue -> issue != null && "error".equalsIgnoreCase(issue.level()));
    }

    private boolean hasIssue(List<GenerationValidationIssue> issues, String code) {
        if (issues == null || issues.isEmpty()) {
            return false;
        }
        return issues.stream()
                .anyMatch(issue -> issue != null && code.equalsIgnoreCase(issue.code()));
    }

    private int blockingIssueCount(List<GenerationValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (GenerationValidationIssue issue : issues) {
            if (issue != null && "error".equalsIgnoreCase(issue.level())) {
                count++;
            }
        }
        return count;
    }

    private boolean isBetterAttempt(SectionAttempt candidate,
                                    SectionAttempt current,
                                    int targetCount) {
        if (candidate == null) {
            return false;
        }
        if (current == null) {
            return true;
        }
        return compareCandidate(
                candidate.questions(), candidate.issues(), current.questions(), current.issues(), targetCount) < 0;
    }

    private boolean isBetterCandidate(List<CreateQuestionRequest> candidateQuestions,
                                      List<GenerationValidationIssue> candidateIssues,
                                      List<CreateQuestionRequest> bestQuestions,
                                      List<GenerationValidationIssue> bestIssues,
                                      int targetCount) {
        return compareCandidate(candidateQuestions, candidateIssues, bestQuestions, bestIssues, targetCount) < 0;
    }

    private int compareCandidate(List<CreateQuestionRequest> candidateQuestions,
                                 List<GenerationValidationIssue> candidateIssues,
                                 List<CreateQuestionRequest> bestQuestions,
                                 List<GenerationValidationIssue> bestIssues,
                                 int targetCount) {
        int candidateBlocking = blockingIssueCount(candidateIssues);
        int bestBlocking = blockingIssueCount(bestIssues);
        if (candidateBlocking != bestBlocking) {
            return Integer.compare(candidateBlocking, bestBlocking);
        }
        int candidateIssueCount = candidateIssues == null ? 0 : candidateIssues.size();
        int bestIssueCount = bestIssues == null ? 0 : bestIssues.size();
        if (candidateIssueCount != bestIssueCount) {
            return Integer.compare(candidateIssueCount, bestIssueCount);
        }
        int candidateGap = Math.abs(targetCount - (candidateQuestions == null ? 0 : candidateQuestions.size()));
        int bestGap = Math.abs(targetCount - (bestQuestions == null ? 0 : bestQuestions.size()));
        if (candidateGap != bestGap) {
            return Integer.compare(candidateGap, bestGap);
        }
        return Integer.compare(bestQuestions == null ? 0 : bestQuestions.size(), candidateQuestions == null ? 0 : candidateQuestions.size());
    }

    private List<String> summarizeIssues(List<GenerationValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        return issues.stream()
                .filter(issue -> issue != null)
                .limit(ISSUE_SUMMARY_LIMIT)
                .map(issue -> {
                    StringBuilder summary = new StringBuilder();
                    if (issue.questionIndex() != null) {
                        summary.append("question ").append(issue.questionIndex()).append(": ");
                    }
                    summary.append(issue.message());
                    if (StringUtils.hasText(issue.repairHint())) {
                        summary.append(" Hint: ").append(issue.repairHint());
                    }
                    return summary.toString();
                })
                .toList();
    }

    private Set<String> collectReferenceSignatures(List<AgentSearchItem> evidences) {
        Set<String> signatures = new LinkedHashSet<>();
        if (evidences == null || evidences.isEmpty()) {
            return signatures;
        }
        for (AgentSearchItem evidence : evidences) {
            if (evidence == null) {
                continue;
            }
            String sourceType = value(evidence.sourceType()).toUpperCase(Locale.ROOT);
            if (!sourceType.contains("QUESTION")) {
                continue;
            }
            String signature = buildSignature(evidence.title(), evidence.snippet());
            if (StringUtils.hasText(signature)) {
                signatures.add(signature);
            }
        }
        return signatures;
    }

    private Set<String> questionSignatures(List<CreateQuestionRequest> questions) {
        Set<String> signatures = new LinkedHashSet<>();
        for (Map<String, Object> question : questionMaps(questions)) {
            String signature = buildSignature(question);
            if (StringUtils.hasText(signature)) {
                signatures.add(signature);
            }
        }
        return signatures;
    }

    private String buildSignature(Map<String, Object> question) {
        if (question == null) {
            return "";
        }
        return buildSignature(value(question.get("questionTitle")), value(question.get("questionContent")));
    }

    private String buildSignature(String title, String content) {
        String normalized = normalizeSignatureText(value(title) + "|" + value(content));
        return normalized.replaceAll("^\\|+", "").replaceAll("\\|+$", "").trim();
    }

    private String normalizeSignatureText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> recentBlockedSignatures(Set<String> blockedSignatures) {
        if (blockedSignatures == null || blockedSignatures.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(blockedSignatures);
        int fromIndex = Math.max(0, values.size() - BLOCKED_SIGNATURE_LIMIT);
        return values.subList(fromIndex, values.size());
    }

    private List<Map<String, Object>> mutableMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(value(entry.getKey()), entry.getValue());
                }
                result.add(copy);
            }
        }
        return result;
    }

    private List<Integer> allocateWeightedIntegers(List<Integer> weights, int targetTotal, List<Integer> minimums) {
        int itemCount = weights == null ? 0 : weights.size();
        if (itemCount == 0) {
            return List.of();
        }
        int minimumTotal = minimums.stream().mapToInt(value -> Math.max(1, value == null ? 1 : value)).sum();
        int effectiveTarget = Math.max(targetTotal, minimumTotal);
        int remaining = effectiveTarget - minimumTotal;
        long totalWeight = weights.stream().mapToLong(value -> Math.max(1, value == null ? 1 : value)).sum();

        List<Integer> allocations = new ArrayList<>(itemCount);
        List<Double> remainders = new ArrayList<>(itemCount);
        int allocatedExtra = 0;
        for (int i = 0; i < itemCount; i++) {
            int minimum = Math.max(1, minimums.get(i) == null ? 1 : minimums.get(i));
            allocations.add(minimum);
            if (remaining == 0 || totalWeight <= 0) {
                remainders.add(0D);
                continue;
            }
            int weight = Math.max(1, weights.get(i) == null ? 1 : weights.get(i));
            double rawExtra = (double) remaining * weight / totalWeight;
            int extra = (int) Math.floor(rawExtra);
            allocations.set(i, minimum + extra);
            remainders.add(rawExtra - extra);
            allocatedExtra += extra;
        }
        distributeLeftoverIntegers(allocations, remainders, remaining - allocatedExtra);
        return allocations;
    }

    private void distributeLeftoverIntegers(List<Integer> allocations, List<Double> remainders, int leftover) {
        while (leftover > 0) {
            int targetIndex = 0;
            double maxRemainder = -1D;
            for (int i = 0; i < remainders.size(); i++) {
                if (remainders.get(i) > maxRemainder) {
                    maxRemainder = remainders.get(i);
                    targetIndex = i;
                }
            }
            allocations.set(targetIndex, allocations.get(targetIndex) + 1);
            remainders.set(targetIndex, 0D);
            leftover--;
        }
    }

    private List<BigDecimal> allocateWeightedScores(List<Long> weights, BigDecimal targetTotal) {
        return allocateWeightedScores(weights, targetTotal, 1L);
    }

    private List<BigDecimal> allocateWeightedScores(List<Long> weights, BigDecimal targetTotal, long minimumPerItem) {
        int itemCount = weights == null ? 0 : weights.size();
        if (itemCount == 0 || targetTotal == null) {
            return List.of();
        }
        int scale = resolveScoreAllocationScale(targetTotal, itemCount);
        long targetUnits = toScaledUnits(targetTotal, scale);
        List<Long> allocations = allocateWeightedUnits(weights, targetUnits, minimumPerItem);
        List<BigDecimal> results = new ArrayList<>(allocations.size());
        for (Long allocation : allocations) {
            results.add(normalizeAllocatedScore(BigDecimal.valueOf(allocation).movePointLeft(scale)));
        }
        return results;
    }

    private List<Long> allocateWeightedUnits(List<Long> weights, long targetUnits, long minimumPerItem) {
        int itemCount = weights == null ? 0 : weights.size();
        if (itemCount == 0) {
            return List.of();
        }
        long minimum = Math.max(0L, minimumPerItem);
        long minimumTotal = itemCount * minimum;
        long effectiveTarget = Math.max(targetUnits, minimumTotal);
        long remaining = effectiveTarget - minimumTotal;
        long totalWeight = weights.stream().mapToLong(value -> Math.max(1L, value == null ? 1L : value)).sum();

        List<Long> allocations = new ArrayList<>(itemCount);
        List<Double> remainders = new ArrayList<>(itemCount);
        long allocatedExtra = 0L;
        for (Long weightValue : weights) {
            allocations.add(minimum);
            if (remaining == 0 || totalWeight <= 0) {
                remainders.add(0D);
                continue;
            }
            long weight = Math.max(1L, weightValue == null ? 1L : weightValue);
            double rawExtra = (double) remaining * weight / totalWeight;
            long extra = (long) Math.floor(rawExtra);
            allocations.set(allocations.size() - 1, minimum + extra);
            remainders.add(rawExtra - extra);
            allocatedExtra += extra;
        }
        distributeLeftoverUnits(allocations, remainders, remaining - allocatedExtra);
        return allocations;
    }

    private void distributeLeftoverUnits(List<Long> allocations, List<Double> remainders, long leftover) {
        while (leftover > 0L) {
            int targetIndex = 0;
            double maxRemainder = -1D;
            for (int i = 0; i < remainders.size(); i++) {
                if (remainders.get(i) > maxRemainder) {
                    maxRemainder = remainders.get(i);
                    targetIndex = i;
                }
            }
            allocations.set(targetIndex, allocations.get(targetIndex) + 1L);
            remainders.set(targetIndex, 0D);
            leftover--;
        }
    }

    private int resolveScoreAllocationScale(BigDecimal targetTotal, int itemCount) {
        BigDecimal normalizedTarget = normalizePositiveScore(targetTotal);
        if (normalizedTarget == null || itemCount <= 0) {
            return 0;
        }
        int scale = Math.max(0, normalizedTarget.stripTrailingZeros().scale());
        if (scale == 0 && normalizedTarget.compareTo(BigDecimal.valueOf(itemCount)) >= 0) {
            return 0;
        }
        scale = Math.max(1, scale);
        while (normalizedTarget.movePointRight(scale).compareTo(BigDecimal.valueOf(itemCount)) < 0) {
            scale++;
        }
        return scale;
    }

    private long toScaledUnits(BigDecimal score, int scale) {
        return normalizePositiveScore(score)
                .movePointRight(scale)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    private BigDecimal normalizePositiveScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal normalized = score.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0, RoundingMode.UNNECESSARY) : normalized;
    }

    private BigDecimal normalizeAllocatedScore(BigDecimal score) {
        if (score == null) {
            return null;
        }
        BigDecimal normalized = score.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0, RoundingMode.UNNECESSARY) : normalized;
    }

    private long toWeight(BigDecimal score) {
        if (score == null) {
            return 1L;
        }
        return Math.max(1L, score.movePointRight(2).setScale(0, RoundingMode.CEILING).longValue());
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(1, maxLength - 3)) + "...";
    }

    private String plain(BigDecimal value) {
        if (value == null) {
            return "";
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0, RoundingMode.UNNECESSARY);
        }
        return normalized.toPlainString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private record SectionSeed(Integer questionType, int count) {
    }

    private record SectionAttempt(List<CreateQuestionRequest> questions,
                                  List<GenerationValidationIssue> issues) {
        private SectionAttempt {
            questions = questions == null ? List.of() : questions;
            issues = issues == null ? List.of() : issues;
        }
    }

    private record NormalizedQuestionBatch(List<CreateQuestionRequest> questions,
                                           List<GenerationValidationIssue> issues) {
    }

    private record GenerationDraftResult(List<CreateQuestionRequest> questions, List<GenerationValidationIssue> issues) {
    }

    private record ReviewResult(List<CreateQuestionRequest> questions, List<GenerationValidationIssue> issues) {
    }
}
