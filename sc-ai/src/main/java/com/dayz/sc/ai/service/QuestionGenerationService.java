package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
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

@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_QUESTION_COUNT = 10;
    private static final int MAX_PAPER_COUNT = 50;
    private static final int MAX_SECTION_BATCH_SIZE = 1;
    private static final int MAX_SECTION_ATTEMPTS = 3;
    private static final int MAX_REPAIR_ATTEMPTS = 2;
    private static final int ISSUE_SUMMARY_LIMIT = 5;
    private static final int BLOCKED_SIGNATURE_LIMIT = 8;
    private static final List<Integer> MIXED_QUESTION_TYPES = List.of(0, 1, 2, 3, 4);
    private static final String QUESTION_OUTPUT_RULES = """
                出题 JSON 输出硬性规则：
                - 顶层只能是 {"questions":[...]}，不要返回 markdown、解释文本或代码块。
                - options 内只能使用 optionContent, optionLabel, isCorrect, score, imageUrls, explanation。
                - answers 内只能使用 answerContent, explanation, score, sortOrder。
                - isCorrect 使用 true/false 布尔值，不要使用 1/0 或 A/B。
                - optionContent 只写选项正文，禁止带 A.、B.、答案、正确答案或“（答案）”等标签。
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

        List<AgentSearchItem> evidences = collectEvidence(
                userMessage, normalized, userId, role, courseId, agentSearchListener, agentSearchEvents);
        Map<String, Object> contextPayload = evidencePayload(evidences, agentSearchEvents);
        emitStage(stageListener, traceEntries, requestId, mode, "CONTEXT_READY", "processing",
                "资料已整理",
                "已整理 " + evidences.size() + " 条课程、题库或知识资料。",
                contextPayload,
                "context_summary");

        PaperBlueprint blueprint = buildBlueprint(userMessage, normalized, context, evidences, paper, requestId);
        Map<String, Object> blueprintPayload = blueprintPayload(blueprint);
        emitStage(stageListener, traceEntries, requestId, mode, "PLANNED", "processing",
                paper ? "试卷蓝图已生成" : "出题方案已生成",
                blueprint.generationStrategy(),
                blueprintPayload,
                "blueprint");

        Set<String> referenceSignatures = collectReferenceSignatures(evidences);
        GenerationDraftResult draftResult = generateDrafts(
                userMessage, normalized, context, evidences, blueprint, paper, traceEntries, debugTraceEntries,
                referenceSignatures, stageListener, requestId, mode);
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
                generatedQuestions, normalized, context, blueprint, issues, referenceSignatures, debugTraceEntries);
        reviewResult = ensureVisibleQuestions(reviewResult, normalized, paper, debugTraceEntries);
        List<CreateQuestionRequest> finalQuestions = reviewResult.questions();
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

    private List<AgentSearchItem> collectEvidence(String userMessage,
                                                  GenerationRequest request,
                                                  UUID userId,
                                                  Integer role,
                                                  UUID courseId,
                                                  Consumer<AgentSearchEvent> listener,
                                                  List<AgentSearchEvent> eventLog) {
        if (userId == null || role == null) {
            return List.of();
        }
        List<AgentSearchItem> evidences = new ArrayList<>();
        String query = searchQuery(userMessage, request);
        addSearchResults(evidences, eventLog, listener, "resources", "正在检索课程资源",
                query, () -> agentSearchService.searchCourseResources(
                        query,
                        courseId,
                        null,
                        List.of("COURSE", "CHAPTER", "QUESTION_BANK", "QUESTION", "COURSE_FILE", "LIVE_PRACTICE"),
                        8,
                        userId,
                        role));
        addSearchResults(evidences, eventLog, listener, "knowledge", "正在检索个人知识库",
                query, () -> agentSearchService.searchPersonalKnowledge(userId, query, 5));
        addSearchResults(evidences, eventLog, listener, "memory", "正在检索历史对话",
                query, () -> agentSearchService.searchChatMemory(userId, query, 5));
        return deduplicateEvidence(evidences);
    }

    private void addSearchResults(List<AgentSearchItem> evidences,
                                  List<AgentSearchEvent> eventLog,
                                  Consumer<AgentSearchEvent> listener,
                                  String domain,
                                  String label,
                                  String query,
                                  EvidenceSupplier supplier) {
        AgentSearchEvent started = AgentSearchEvent.started(domain, label, query);
        recordAgentSearch(eventLog, listener, started);
        try {
            List<AgentSearchItem> results = supplier.get();
            AgentSearchEvent resultEvent = AgentSearchEvent.results(
                    started.searchId(),
                    domain,
                    results.isEmpty() ? "未找到匹配资料" : "找到 " + results.size() + " 条资料",
                    query,
                    results);
            recordAgentSearch(eventLog, listener, resultEvent);
            evidences.addAll(results);
        } catch (RuntimeException e) {
            recordAgentSearch(eventLog, listener, AgentSearchEvent.error(started.searchId(), domain, label + "失败", query));
        }
    }

    private void recordAgentSearch(List<AgentSearchEvent> eventLog,
                                   Consumer<AgentSearchEvent> listener,
                                   AgentSearchEvent event) {
        eventLog.add(event);
        if (listener != null) {
            listener.accept(event);
        }
    }

    private List<AgentSearchItem> deduplicateEvidence(List<AgentSearchItem> evidences) {
        Map<String, AgentSearchItem> deduped = new LinkedHashMap<>();
        for (AgentSearchItem item : evidences) {
            if (item == null) {
                continue;
            }
            String key = value(item.sourceType()) + "|" + value(item.sourceId()) + "|" + value(item.title());
            deduped.putIfAbsent(key, item);
        }
        return new ArrayList<>(deduped.values());
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
                : recommendedTotalEstimatedTime(request, totalCount);
        List<PaperSectionPlan> sections = paperSections(request, context, evidences, totalCount, totalScore, totalEstimatedTime);
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
                                                 int totalCount,
                                                 BigDecimal totalScore,
                                                 Integer totalEstimatedTime) {
        List<Integer> types = request.questionType() != null && request.questionType() != 5
                ? List.of(normalizeQuestionType(request.questionType(), 0))
                : MIXED_QUESTION_TYPES.subList(0, Math.min(totalCount, MIXED_QUESTION_TYPES.size()));
        List<SectionSeed> seeds = new ArrayList<>();
        if (types.size() == 1) {
            int remaining = totalCount;
            while (remaining > 0) {
                int count = Math.min(MAX_SECTION_BATCH_SIZE, remaining);
                seeds.add(new SectionSeed(types.getFirst(), count));
                remaining -= count;
            }
        } else {
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
        }

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
        List<Long> weights = seeds.stream().map(seed -> (long) Math.max(1, seed.count())).toList();
        return allocateWeightedScores(weights, target);
    }

    private int recommendedTotalEstimatedTime(GenerationRequest request, int totalCount) {
        if (request.questionType() != null && request.questionType() != 5) {
            return totalCount * recommendEstimatedTime(request.questionType(), request.difficulty());
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
                                                 String mode) {
        List<CreateQuestionRequest> questions = new ArrayList<>();
        List<GenerationValidationIssue> allIssues = new ArrayList<>();
        Set<String> blockedSignatures = new LinkedHashSet<>(referenceSignatures);
        for (PaperSectionPlan section : blueprint.sections()) {
            SectionAttempt bestAttempt = null;
            List<String> retryHints = List.of();
            for (int attemptNo = 1; attemptNo <= MAX_SECTION_ATTEMPTS; attemptNo++) {
                SectionAttempt attempt = generateSectionAttempt(
                        userMessage, request, context, evidences, blueprint, section, paper, blockedSignatures, retryHints, attemptNo);
                appendTraceEntry(debugTraceEntries, "GENERATED", "questionGeneration", "section_attempt",
                        "第 " + section.sectionNo() + " 部分，第 " + attemptNo + " 次生成",
                        "本次产出 " + attempt.questions().size() + " 道草稿题目，发现 "
                                + attempt.issues().size() + " 个校验问题。",
                        sectionAttemptPayload(section, attemptNo, attempt.questions(), attempt.issues()));
                if (isBetterAttempt(attempt, bestAttempt, section.targetCount())) {
                    bestAttempt = attempt;
                }
                if (!hasBlockingIssues(attempt.issues())) {
                    break;
                }
                retryHints = summarizeIssues(attempt.issues());
            }
            if (bestAttempt != null) {
                questions.addAll(bestAttempt.questions());
                allIssues.addAll(bestAttempt.issues());
                blockedSignatures.addAll(questionSignatures(bestAttempt.questions()));
                publishDraftProgress(stageListener, traceEntries, requestId, mode, section, questions,
                        bestAttempt.questions(), bestAttempt.issues(), blueprint.totalQuestionCount());
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
                                                  int attemptNo) {
        try {
            String response = callSectionModel(
                    userMessage, request, context, evidences, blueprint, section, paper, blockedSignatures, retryHints, attemptNo);
            Map<String, Object> payload = parsePayload(response);
            GenerationRequest sectionRequest = sectionRequest(request, section);
            List<CreateQuestionRequest> questions = normalizeQuestions(payload, sectionRequest, false, false);
            questions = applySectionDefaults(questions, request, section);
            questions = rebalanceQuestionScores(questions, sectionRequest);
            questions = rebalanceEstimatedTimes(questions, section.totalEstimatedTime());
            List<GenerationValidationIssue> issues = validateQuestions(
                    questions, sectionRequest, section.targetCount(), null, section.totalEstimatedTime(), blockedSignatures);
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

    private String callSectionModel(String userMessage,
                                    GenerationRequest request,
                                    AiCourseContext context,
                                    List<AgentSearchItem> evidences,
                                    PaperBlueprint blueprint,
                                    PaperSectionPlan section,
                                    boolean paper,
                                    Set<String> blockedSignatures,
                                    List<String> retryHints,
                                    int attemptNo) {
        String prompt = """
                你是教育测评出题助手。当前任务：只为试卷蓝图中的一个部分生成题目。
                只返回合法 JSON，不要使用 markdown 代码块，不要输出解释。
                顶层结构必须是：{"questions":[...]}。
                questions 数组长度必须等于当前部分 targetCount。

                每道题必须完全符合当前项目的题目创建结构：
                - questionBankId, questionTitle, questionContent, questionType, difficulty, score, estimatedTime, tags, imageUrls, allowPartialCredit, options, answers
                - options 内只能使用 optionContent, optionLabel, isCorrect, score, imageUrls, explanation
                - answers 内只能使用 answerContent, explanation, score, sortOrder
                - 单题 questionType 只能是 0-4：0 单选，1 多选，2 判断，3 填空，4 简答
                - questionBankId 必须等于生成参数中的 questionBankId；如果参数为空则返回 null
                - difficulty 必须是 1、2、3
                - score 必须为正数，estimatedTime 必须为正整数分钟
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
                toJson(blueprintPayload(blueprint)),
                toJson(section),
                platformDataTool.summarize(context),
                toJson(summarizeEvidences(evidences, 8)),
                toJson(recentBlockedSignatures(blockedSignatures)),
                toJson(retryHints));
        return aiProviderCallGuard.call(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());
    }

    private Map<String, Object> parsePayload(String response) {
        if (!StringUtils.hasText(response)) {
            return mapWithQuestions(List.of());
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("(?i)^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        json = extractJsonObject(json);
        try {
            AiGeneratedQuestionPayload payload = generatedQuestionReader().readValue(json);
            return generatedPayloadToMap(payload);
        } catch (JsonProcessingException exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", "AI generation result");
            fallback.put("raw", response);
            fallback.put("parseError", exception.getOriginalMessage());
            fallback.put("questions", List.of());
            return fallback;
        }
    }

    private String extractJsonObject(String value) {
        int start = value.indexOf('{');
        if (start < 0) {
            return value;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = inString;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return value.substring(start, i + 1).trim();
                }
            }
        }
        return value;
    }

    private ObjectReader generatedQuestionReader() {
        return objectMapper.readerFor(AiGeneratedQuestionPayload.class)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private Map<String, Object> mapWithQuestions(List<Map<String, Object>> questions) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questions", questions);
        return payload;
    }

    private Map<String, Object> generatedPayloadToMap(AiGeneratedQuestionPayload payload) {
        if (payload == null) {
            return mapWithQuestions(List.of());
        }
        return mapWithQuestions(payload.questions().stream()
                .filter(question -> question != null)
                .map(this::generatedQuestionToMap)
                .toList());
    }

    private Map<String, Object> generatedQuestionToMap(AiGeneratedQuestion question) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("questionBankId", question.questionBankId());
        map.put("questionTitle", question.questionTitle());
        map.put("questionContent", question.questionContent());
        map.put("questionType", question.questionType());
        map.put("difficulty", question.difficulty());
        map.put("score", question.score());
        map.put("estimatedTime", question.estimatedTime());
        map.put("tags", question.tags());
        map.put("imageUrls", question.imageUrls());
        map.put("allowPartialCredit", question.allowPartialCredit());
        map.put("options", question.options().stream().map(this::generatedOptionToMap).toList());
        map.put("answers", question.answers().stream().map(this::generatedAnswerToMap).toList());
        return map;
    }

    private Map<String, Object> generatedOptionToMap(AiGeneratedOption option) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("optionContent", option.optionContent());
        map.put("optionLabel", option.optionLabel());
        map.put("isCorrect", option.isCorrect());
        map.put("score", option.score());
        map.put("imageUrls", option.imageUrls());
        map.put("explanation", option.explanation());
        return map;
    }

    private Map<String, Object> generatedAnswerToMap(AiGeneratedAnswer answer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("answerContent", answer.answerContent());
        map.put("explanation", answer.explanation());
        map.put("score", answer.score());
        map.put("sortOrder", answer.sortOrder());
        return map;
    }

    private List<CreateQuestionRequest> normalizeQuestions(Map<String, Object> payload,
                                                           GenerationRequest request,
                                                           boolean paper) {
        return normalizeQuestions(payload, request, paper, true);
    }

    private List<CreateQuestionRequest> normalizeQuestions(Map<String, Object> payload,
                                                           GenerationRequest request,
                                                           boolean paper,
                                                           boolean allowFallback) {
        Object value = payload.get("questions");
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return allowFallback ? fallbackQuestions(payload, request, paper) : List.of();
        }
        List<CreateQuestionRequest> questions = new ArrayList<>();
        int index = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                questions.add(normalizeQuestion(map, request, index));
                index++;
            }
        }
        return questions.isEmpty() && allowFallback ? fallbackQuestions(payload, request, paper) : questions;
    }

    private List<CreateQuestionRequest> fallbackQuestions(Map<String, Object> payload,
                                                          GenerationRequest request,
                                                          boolean paper) {
        String raw = value(payload.get("raw"));
        Map<String, Object> question = new LinkedHashMap<>();
        int questionType = normalizeQuestionType(request.questionType(), 4);
        question.put("questionBankId", request.questionBankId() == null ? null : request.questionBankId().toString());
        question.put("questionTitle", paper ? "试卷生成结果需要人工整理" : "题目生成结果需要人工整理");
        question.put("questionContent", StringUtils.hasText(raw) ? raw : "AI 未返回可解析的题目结构，请调整要求后重试。");
        question.put("questionType", questionType);
        question.put("difficulty", normalizeDifficulty(request.difficulty(), 2));
        question.put("score", scorePerQuestion(request));
        question.put("estimatedTime", recommendEstimatedTime(questionType, request.difficulty()));
        question.put("tags", safeList(request.knowledgePoints()));
        question.put("imageUrls", List.of());
        question.put("allowPartialCredit", questionType == 1 ? 1 : 0);
        question.put("options", fallbackOptions(questionType));
        question.put("answers", List.of(Map.of(
                "answerContent", "请教师根据题干补充参考答案。",
                "explanation", "",
                "score", scorePerQuestion(request),
                "sortOrder", 1)));
        normalizeQuestionStructure(question, request);
        return List.of(toCreateQuestionRequest(question));
    }

    private List<Map<String, Object>> fallbackOptions(int questionType) {
        return switch (questionType) {
            case 0 -> List.of(
                    fallbackOption("A", "璇锋暀甯堟牴鎹骞茶ˉ鍏呮纭€夐」", true),
                    fallbackOption("B", "璇锋暀甯堟牴鎹骞茶ˉ鍏呭共鎵伴€夐」", false));
            case 1 -> List.of(
                    fallbackOption("A", "璇锋暀甯堟牴鎹骞茶ˉ鍏呮纭€夐」", true),
                    fallbackOption("B", "璇锋暀甯堟牴鎹骞茶ˉ鍏呯浜屼釜姝ｇ‘閫夐」", true),
                    fallbackOption("C", "璇锋暀甯堟牴鎹骞茶ˉ鍏呭共鎵伴€夐」", false));
            case 2 -> List.of(
                    fallbackOption("A", "姝ｇ‘", true),
                    fallbackOption("B", "閿欒", false));
            default -> List.of();
        };
    }

    private Map<String, Object> fallbackOption(String label, String content, boolean correct) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("optionLabel", label);
        option.put("optionContent", content);
        option.put("isCorrect", correct ? 1 : 0);
        option.put("score", BigDecimal.ZERO);
        option.put("imageUrls", List.of());
        option.put("explanation", "");
        return option;
    }

    private CreateQuestionRequest normalizeQuestion(Map<?, ?> source, GenerationRequest request, int index) {
        Map<String, Object> question = new LinkedHashMap<>();
        int fallbackType = request.questionType() == null || request.questionType() == 5 ? 4 : request.questionType();
        int questionType = normalizeQuestionType(intValue(source.get("questionType"), fallbackType), fallbackType);
        BigDecimal score = decimalValue(source.get("score"), scorePerQuestion(request));
        question.put("questionBankId", questionBankIdValue(source, request));
        question.put("questionTitle", text(source, "questionTitle", "题目 " + (index + 1)));
        question.put("questionContent", text(source, "questionContent", text(source, "content", "")));
        question.put("questionType", questionType);
        question.put("difficulty", normalizeDifficulty(intValue(source.get("difficulty"), request.difficulty() == null ? 2 : request.difficulty()), 2));
        question.put("score", score);
        question.put("estimatedTime", Math.max(1, intValue(source.get("estimatedTime"), recommendEstimatedTime(questionType, request.difficulty()))));
        question.put("tags", normalizeStringList(source.get("tags"), request.knowledgePoints()));
        question.put("imageUrls", normalizeStringList(source.get("imageUrls"), List.of()));
        question.put("allowPartialCredit", normalizeFlag(intValue(source.get("allowPartialCredit"), questionType == 1 ? 1 : 0)));
        question.put("options", normalizeOptions(source.get("options")));
        question.put("answers", normalizeAnswers(source.get("answers"), score));
        normalizeQuestionStructure(question, request);
        return toCreateQuestionRequest(question);
    }

    private void normalizeQuestionStructure(Map<String, Object> question, GenerationRequest request) {
        int type = intValue(question.get("questionType"), 4);
        BigDecimal score = decimalValue(question.get("score"), scorePerQuestion(request));
        if (type <= 2) {
            List<Map<String, Object>> options = mutableMapList(question.get("options"));
            question.put("options", options);
            if (options.isEmpty()) {
                question.put("answers", List.of());
                return;
            }
            if (type == 2) {
                normalizeJudgeOptions(question);
            }
            synchronizeOptionScores(question);
            question.put("answers", List.of(Map.of(
                    "answerContent", defaultAnswerContent(question),
                    "explanation", firstCorrectOptionExplanation(question),
                    "score", score,
                    "sortOrder", 1)));
            return;
        }
        question.put("options", List.of());
        if (type == 3) {
            return;
        }
        if (!(question.get("answers") instanceof List<?> answers) || answers.isEmpty()) {
            question.put("answers", List.of(Map.of(
                    "answerContent", "请教师根据题干补充参考答案。",
                    "explanation", "",
                    "score", score,
                    "sortOrder", 1)));
        }
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
                option.put("isCorrect", normalizeCorrectFlag(map.get("isCorrect"), map.get("correct")));
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
        boolean trueIsCorrect = inferJudgeAnswer(question, mutableMapList(question.get("options")));
        question.put("options", List.of(
                judgeOption("A", "正确", trueIsCorrect),
                judgeOption("B", "错误", !trueIsCorrect)));
    }

    private Map<String, Object> judgeOption(String label, String content, boolean correct) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("optionLabel", label);
        option.put("optionContent", content);
        option.put("isCorrect", correct ? 1 : 0);
        option.put("score", BigDecimal.ZERO);
        option.put("imageUrls", List.of());
        option.put("explanation", "");
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
        return mutableMapList(question.get("options")).stream()
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
        for (Map<String, Object> question : questions) {
            question.put("questionType", normalizeQuestionType(intValue(question.get("questionType"), section.questionType()), section.questionType()));
            question.put("difficulty", normalizeDifficulty(intValue(question.get("difficulty"), section.difficulty()), section.difficulty()));
            question.put("estimatedTime", Math.max(1, intValue(question.get("estimatedTime"), section.estimatedTimePerQuestion())));
            if (section.scorePerQuestion() != null) {
                question.put("score", section.scorePerQuestion());
            }
            if (!StringUtils.hasText(value(question.get("questionTitle")))) {
                question.put("questionTitle", "题目 " + (questions.indexOf(question) + 1));
            }
            if (!StringUtils.hasText(value(question.get("questionContent")))) {
                question.put("questionContent", question.get("questionTitle"));
            }
            if (question.get("tags") instanceof List<?> tags && !tags.isEmpty()) {
                continue;
            }
            question.put("tags", !safeList(section.knowledgePoints()).isEmpty() ? section.knowledgePoints() : safeList(request.knowledgePoints()));
        }
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
                                         List<GenerationTraceEntry> debugTraceEntries) {
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
        if (!hasBlockingIssues(currentIssues)) {
            appendTraceEntry(debugTraceEntries, "REPAIRED", "paperReview", "repair_summary",
                    "修复前复核",
                    "草稿题目已通过校验，无需额外修复。",
                    finalQuestionsPayload(currentQuestions, currentIssues));
            return new ReviewResult(currentQuestions, currentIssues);
        }

        List<CreateQuestionRequest> bestQuestions = currentQuestions;
        List<GenerationValidationIssue> bestIssues = currentIssues;
        for (int attemptNo = 1; attemptNo <= MAX_REPAIR_ATTEMPTS && hasBlockingIssues(currentIssues); attemptNo++) {
            try {
                Map<String, Object> repairedPayload = parsePayload(callRepairModel(
                        request, context, blueprint, questionMaps(currentQuestions), currentIssues, attemptNo));
                List<CreateQuestionRequest> repairedQuestions = normalizeQuestions(repairedPayload, request, false, false);
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
        return new ReviewResult(bestQuestions, bestIssues);
    }

    private ReviewResult ensureVisibleQuestions(ReviewResult result,
                                                GenerationRequest request,
                                                boolean paper,
                                                List<GenerationTraceEntry> debugTraceEntries) {
        if (result.questions() != null && !result.questions().isEmpty()) {
            return result;
        }
        List<CreateQuestionRequest> fallback = fallbackQuestions(Map.of(), request, paper);
        fallback = rebalanceQuestionScores(fallback, request);
        fallback = rebalanceEstimatedTimes(fallback, request.totalEstimatedTime());
        List<GenerationValidationIssue> issues = new ArrayList<>(result.issues());
        issues.add(issue(
                "AI_GENERATION_PLACEHOLDER",
                "warning",
                "AI did not return a usable question structure, so a visible placeholder question was created.",
                1,
                "Review the AI output and generation requirements, then retry and replace the placeholder."));
        appendTraceEntry(debugTraceEntries, "REPAIRED", "paperReview", "placeholder_result",
                "Placeholder question created",
                "AI returned no usable questions; a visible placeholder was added to avoid an empty completed result.",
                finalQuestionsPayload(fallback, issues));
        return new ReviewResult(fallback, issues);
    }

    private String callRepairModel(GenerationRequest request,
                                   AiCourseContext context,
                                   PaperBlueprint blueprint,
                                   List<Map<String, Object>> currentQuestions,
                                   List<GenerationValidationIssue> issues,
                                   int attemptNo) {
        String prompt = """
                你是教育测评题目修复助手。当前任务：修复已经生成的题目集合。
                只返回合法 JSON，不要使用 markdown 代码块，不要输出解释。
                顶层结构必须是：{"questions":[...]}。
                questions 数组必须包含 exactly %s 道题。
                修复结构问题时尽量保留原始教学意图。
                每道题必须完整符合当前项目题目创建结构，并且 score/estimatedTime 为正数。
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
        return aiProviderCallGuard.call(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());
    }

    private List<CreateQuestionRequest> deterministicRepair(List<CreateQuestionRequest> questions, GenerationRequest request) {
        return createQuestionRequests(deterministicRepairMaps(questionMaps(questions), request));
    }

    private List<Map<String, Object>> deterministicRepairMaps(List<Map<String, Object>> questions, GenerationRequest request) {
        List<Map<String, Object>> repaired = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> copy = new LinkedHashMap<>(questions.get(i));
            if (!StringUtils.hasText(value(copy.get("questionTitle")))) {
                copy.put("questionTitle", "未命名题目 " + (i + 1));
            }
            if (!StringUtils.hasText(value(copy.get("questionContent")))) {
                copy.put("questionContent", copy.get("questionTitle"));
            }
            if (request.questionBankId() != null) {
                copy.put("questionBankId", request.questionBankId().toString());
            }
            copy.put("questionType", normalizeQuestionType(intValue(copy.get("questionType"), request.questionType()), 4));
            copy.put("difficulty", normalizeDifficulty(intValue(copy.get("difficulty"), request.difficulty()), 2));
            copy.put("score", decimalValue(copy.get("score"), scorePerQuestion(request)));
            copy.put("estimatedTime", Math.max(1, intValue(copy.get("estimatedTime"),
                    recommendEstimatedTime(intValue(copy.get("questionType"), 4), intValue(copy.get("difficulty"), 2)))));
            normalizeQuestionStructure(copy, request);
            repaired.add(copy);
        }
        return repaired;
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
                    return score != null ? toWeight(score) : (long) recommendEstimatedTime(
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
            weights.add(Math.max(1, current));
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
        GenerationStageEvent event = GenerationStageEvent.of(requestId, mode, stage, status, title, summary, payload);
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
        if (latestQuestions == null || latestQuestions.isEmpty()) {
            return;
        }
        emitStage(listener, traceEntries, requestId, mode, "GENERATED", "processing",
                "题目草稿生成中",
                "已生成 " + allQuestions.size() + " / " + totalQuestionCount + " 道题目草稿。",
                draftedQuestionsPayload(section, allQuestions, latestQuestions, latestIssues, totalQuestionCount),
                "draft_progress");
    }

    private Map<String, Object> draftedQuestionsPayload(PaperSectionPlan section,
                                                        List<CreateQuestionRequest> allQuestions,
                                                        List<CreateQuestionRequest> latestQuestions,
                                                        List<GenerationValidationIssue> latestIssues,
                                                        int totalQuestionCount) {
        return Map.of(
                "sectionNo", section.sectionNo(),
                "questionCount", latestQuestions.size(),
                "generatedQuestionCount", allQuestions.size(),
                "totalQuestionCount", totalQuestionCount,
                "questionDelta", true,
                "questions", questionMaps(latestQuestions),
                "issues", latestIssues == null ? List.of() : latestIssues);
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
                                                      List<GenerationValidationIssue> issues) {
        return Map.of(
                "section", section,
                "attemptNo", attemptNo,
                "questionCount", questions.size(),
                "questions", summarizeQuestions(questions, 6),
                "issues", issues);
    }

    private Map<String, Object> repairAttemptPayload(int attemptNo,
                                                     List<CreateQuestionRequest> questions,
                                                     List<GenerationValidationIssue> issues) {
        return Map.of(
                "attemptNo", attemptNo,
                "questionCount", questions.size(),
                "questions", summarizeQuestions(questions, 8),
                "issues", issues);
    }

    private Map<String, Object> finalQuestionsPayload(List<CreateQuestionRequest> questions,
                                                      List<GenerationValidationIssue> issues) {
        return Map.of(
                "questionCount", questions.size(),
                "questions", summarizeQuestions(questions, 12),
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

    private String searchQuery(String userMessage, GenerationRequest request) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(userMessage)) {
            parts.add(userMessage.strip());
        }
        if (StringUtils.hasText(request.requirement())) {
            parts.add(request.requirement().strip());
        }
        if (request.knowledgePoints() != null) {
            parts.addAll(request.knowledgePoints());
        }
        return parts.isEmpty() ? "课程 知识点 题库 题目" : String.join(" ", parts);
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

    private int normalizeCorrectFlag(Object isCorrect, Object correct) {
        if (isCorrect instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (isCorrect != null && StringUtils.hasText(isCorrect.toString())) {
            return normalizeFlag(intValue(isCorrect, 0));
        }
        return booleanValue(correct, false) ? 1 : 0;
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

    private boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null && StringUtils.hasText(value.toString())) {
            return Boolean.parseBoolean(value.toString());
        }
        return fallback;
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
        return issues.stream().anyMatch(issue -> issue != null && (
                "error".equalsIgnoreCase(issue.level())
                        || "DUPLICATE_QUESTION".equalsIgnoreCase(issue.code())
                        || "REFERENCE_DUPLICATE".equalsIgnoreCase(issue.code())));
    }

    private int blockingIssueCount(List<GenerationValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (GenerationValidationIssue issue : issues) {
            if (issue != null && ("error".equalsIgnoreCase(issue.level())
                    || "DUPLICATE_QUESTION".equalsIgnoreCase(issue.code())
                    || "REFERENCE_DUPLICATE".equalsIgnoreCase(issue.code()))) {
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

    @FunctionalInterface
    private interface EvidenceSupplier {
        List<AgentSearchItem> get();
    }

    private record SectionSeed(Integer questionType, int count) {
    }

    private record SectionAttempt(List<CreateQuestionRequest> questions, List<GenerationValidationIssue> issues) {
        private SectionAttempt {
            questions = questions == null ? List.of() : questions;
            issues = issues == null ? List.of() : issues;
        }
    }

    private record AiGeneratedQuestionPayload(List<AiGeneratedQuestion> questions) {
        private AiGeneratedQuestionPayload {
            questions = questions == null ? List.of() : questions;
        }
    }

    private record AiGeneratedQuestion(String questionBankId,
                                       String questionTitle,
                                       String questionContent,
                                       Integer questionType,
                                       Integer difficulty,
                                       BigDecimal score,
                                       Integer estimatedTime,
                                       List<String> tags,
                                       List<String> imageUrls,
                                       Integer allowPartialCredit,
                                       List<AiGeneratedOption> options,
                                       List<AiGeneratedAnswer> answers) {
        private AiGeneratedQuestion {
            tags = tags == null ? List.of() : tags;
            imageUrls = imageUrls == null ? List.of() : imageUrls;
            options = options == null ? List.of() : options;
            answers = answers == null ? List.of() : answers;
        }
    }

    private record AiGeneratedOption(String optionContent,
                                     String optionLabel,
                                     Boolean isCorrect,
                                     BigDecimal score,
                                     List<String> imageUrls,
                                     String explanation) {
        private AiGeneratedOption {
            imageUrls = imageUrls == null ? List.of() : imageUrls;
        }
    }

    private record AiGeneratedAnswer(String answerContent,
                                     String explanation,
                                     BigDecimal score,
                                     Integer sortOrder) {
    }

    private record GenerationDraftResult(List<CreateQuestionRequest> questions, List<GenerationValidationIssue> issues) {
    }

    private record ReviewResult(List<CreateQuestionRequest> questions, List<GenerationValidationIssue> issues) {
    }
}
