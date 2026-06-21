package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
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
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

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
        return run(userMessage, request, context, false, userId, role, courseId, stageListener, agentSearchListener);
    }

    public AiAgentResult generatePaper(String userMessage,
                                       GenerationRequest request,
                                       AiCourseContext context,
                                       UUID userId,
                                       Integer role,
                                       UUID courseId,
                                       Consumer<GenerationStageEvent> stageListener,
                                       Consumer<AgentSearchEvent> agentSearchListener) {
        return run(userMessage, request, context, true, userId, role, courseId, stageListener, agentSearchListener);
    }

    private AiAgentResult run(String userMessage,
                              GenerationRequest request,
                              AiCourseContext context,
                              boolean paper,
                              UUID userId,
                              Integer role,
                              UUID courseId,
                              Consumer<GenerationStageEvent> stageListener,
                              Consumer<AgentSearchEvent> agentSearchListener) {
        AiMessageType messageType = paper ? AiMessageType.PAPER : AiMessageType.QUESTION_SET;
        String mode = paper ? AiAgentMode.PAPER.name() : AiAgentMode.QUESTION.name();
        String requestId = UuidV7Generator.generate().toString();
        GenerationRequest normalized = normalize(request, paper);
        List<GenerationTraceEntry> traceEntries = new ArrayList<>();
        List<AgentSearchEvent> agentSearchEvents = new ArrayList<>();

        emitStage(stageListener, traceEntries, requestId, mode, "RECEIVED", "processing",
                paper ? "接收出卷请求" : "接收出题请求",
                paper ? "已收到试卷生成参数，准备整理课程资料。" : "已收到题目生成参数，准备整理课程资料。",
                requestPayload(normalized));

        if (!aiRuntimeGuard.isConfigured()) {
            String message = aiRuntimeGuard.missingKeyMessage();
            emitStage(stageListener, traceEntries, requestId, mode, "FAILED", "error",
                    "AI 服务未配置", message, Map.of("status", "AI_NOT_CONFIGURED"));
            return new AiAgentResult(message, messageType, Map.of(
                    "status", "AI_NOT_CONFIGURED",
                    "message", message,
                    "generationTrace", traceEntries));
        }

        List<AgentSearchItem> evidences = collectEvidence(userMessage, normalized, userId, role, courseId, agentSearchListener, agentSearchEvents);
        emitStage(stageListener, traceEntries, requestId, mode, "CONTEXT_READY", "processing",
                "资料已整理",
                "已整理 " + evidences.size() + " 条课程、题库或知识资料。",
                evidencePayload(evidences, agentSearchEvents));

        PaperBlueprint blueprint = buildBlueprint(userMessage, normalized, context, evidences, paper, requestId);
        emitStage(stageListener, traceEntries, requestId, mode, "PLANNED", "processing",
                paper ? "试卷蓝图已生成" : "出题方案已生成",
                blueprint.generationStrategy(),
                blueprintPayload(blueprint));

        Map<String, Object> modelPayload = parsePayload(callModel(userMessage, normalized, context, evidences, blueprint, paper));
        List<Map<String, Object>> generatedQuestions = normalizeQuestions(modelPayload, normalized, paper);
        List<GeneratedQuestionDraft> drafts = draftQuestions(generatedQuestions);
        emitStage(stageListener, traceEntries, requestId, mode, "GENERATED", "processing",
                "题目草稿已生成",
                "已生成 " + generatedQuestions.size() + " 道题目草稿。",
                draftPayload(drafts));

        List<GenerationValidationIssue> issues = validateQuestions(generatedQuestions, normalized, paper);
        emitStage(stageListener, traceEntries, requestId, mode, "VALIDATED", "processing",
                "题目质量校验完成",
                issues.isEmpty() ? "未发现结构性问题。" : "发现 " + issues.size() + " 个需要注意的问题。",
                validationPayload(generatedQuestions, issues));

        List<Map<String, Object>> finalQuestions = repairQuestions(generatedQuestions, normalized, issues);
        emitStage(stageListener, traceEntries, requestId, mode, "REPAIRED", "processing",
                "题目草稿已修正",
                "已根据校验结果完成可自动处理的修正。",
                questionPayload(finalQuestions, issues));

        Map<String, Object> payload = finalPayload(modelPayload, normalized, blueprint, finalQuestions, issues, traceEntries, agentSearchEvents, paper);
        emitStage(stageListener, traceEntries, requestId, mode, "ASSEMBLED", "processing",
                paper ? "试卷已组装" : "题目集已组装",
                "最终结果包含 " + finalQuestions.size() + " 道题目。",
                questionPayload(finalQuestions, issues));

        String content = formatQuestions(payload, paper);
        emitStage(stageListener, traceEntries, requestId, mode, "RESPONDED", "completed",
                "生成完成",
                paper ? "试卷草稿已保存到当前 AI 对话。" : "题目草稿已保存到当前 AI 对话。",
                Map.of("questionCount", finalQuestions.size()));
        payload.put("generationTrace", traceEntries);

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
        addSearchResults(evidences, eventLog, listener, "resources", "正在检索课程资源",
                userMessage, () -> agentSearchService.searchCourseResources(
                        searchQuery(userMessage, request),
                        courseId,
                        null,
                        List.of("COURSE", "CHAPTER", "QUESTION_BANK", "QUESTION", "COURSE_FILE", "LIVE_PRACTICE"),
                        8,
                        userId,
                        role));
        addSearchResults(evidences, eventLog, listener, "knowledge", "正在检索个人知识库",
                userMessage, () -> agentSearchService.searchPersonalKnowledge(userId, searchQuery(userMessage, request), 5));
        addSearchResults(evidences, eventLog, listener, "memory", "正在检索历史对话",
                userMessage, () -> agentSearchService.searchChatMemory(userId, searchQuery(userMessage, request), 5));
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
                : scorePerQuestion(request).multiply(BigDecimal.valueOf(totalCount));
        Integer totalEstimatedTime = request.totalEstimatedTime() != null
                ? request.totalEstimatedTime()
                : Math.max(1, totalCount * 3);
        List<PaperSectionPlan> sections = paper
                ? paperSections(request, totalCount, totalScore, totalEstimatedTime)
                : List.of(new PaperSectionPlan(
                        1,
                        questionTypeName(request.questionType()),
                        request.questionType(),
                        request.difficulty(),
                        totalCount,
                        scorePerQuestion(request),
                        Math.max(1, totalEstimatedTime / Math.max(1, totalCount)),
                        totalEstimatedTime,
                        knowledgePoints(request, context, evidences)));
        String title = StringUtils.hasText(request.paperName())
                ? request.paperName().strip()
                : paper ? "AI 试卷草稿" : "AI 题目草稿";
        String strategy = paper
                ? "先依据课程资料制定试卷蓝图，再按题型与知识点生成题目并校验修正。"
                : "依据课程资料、题库样例和生成要求生成题目草稿，并完成结构校验。";
        if (StringUtils.hasText(userMessage)) {
            strategy = strategy + " 生成要求：" + abbreviate(userMessage, 120);
        }
        return new PaperBlueprint(requestId, title, strategy, totalCount, totalScore, totalEstimatedTime, sections);
    }

    private List<PaperSectionPlan> paperSections(GenerationRequest request,
                                                 int totalCount,
                                                 BigDecimal totalScore,
                                                 Integer totalEstimatedTime) {
        List<PaperSectionPlan> sections = new ArrayList<>();
        List<Integer> types = request.questionType() != null && request.questionType() != 5
                ? List.of(request.questionType())
                : List.of(0, 1, 2, 3, 4);
        int remaining = totalCount;
        int sectionNo = 1;
        for (Integer type : types) {
            if (remaining <= 0) {
                break;
            }
            int sectionsLeft = types.size() - sectionNo + 1;
            int count = Math.max(1, remaining / Math.max(1, sectionsLeft));
            BigDecimal score = totalScore.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP);
            Integer time = Math.max(1, totalEstimatedTime / Math.max(1, totalCount));
            sections.add(new PaperSectionPlan(
                    sectionNo,
                    questionTypeName(type),
                    type,
                    request.difficulty(),
                    count,
                    score,
                    time,
                    count * time,
                    request.knowledgePoints() == null ? List.of() : request.knowledgePoints()));
            remaining -= count;
            sectionNo++;
        }
        if (remaining > 0 && !sections.isEmpty()) {
            PaperSectionPlan last = sections.removeLast();
            int count = last.targetCount() + remaining;
            sections.add(new PaperSectionPlan(
                    last.sectionNo(),
                    last.sectionTitle(),
                    last.questionType(),
                    last.difficulty(),
                    count,
                    last.scorePerQuestion(),
                    last.estimatedTimePerQuestion(),
                    count * Math.max(1, last.estimatedTimePerQuestion()),
                    last.knowledgePoints()));
        }
        return sections;
    }

    private String callModel(String userMessage,
                             GenerationRequest request,
                             AiCourseContext context,
                             List<AgentSearchItem> evidences,
                             PaperBlueprint blueprint,
                             boolean paper) {
        String prompt = """
                你是教育测评出题助手。只返回合法 JSON，不要使用 markdown 代码块。
                顶层 JSON 结构：
                {
                  "title": "string",
                  "type": "question_set or paper",
                  "questions": [
                    {
                      "questionTitle": "string",
                      "questionContent": "string",
                      "questionType": 0,
                      "difficulty": 2,
                      "score": 5,
                      "estimatedTime": 3,
                      "tags": ["string"],
                      "options": [{"label":"A","content":"string","correct":false,"explanation":"string"}],
                      "answers": [{"content":"string","explanation":"string","score":5}]
                    }
                  ]
                }
                题型编码：0 单选题，1 多选题，2 判断题，3 填空题，4 简答题，5 混合题。
                所有展示给用户的标题、题干、答案和解析必须使用自然课堂语言。
                不要在用户可见内容中提到后端字段、ID、JSON、API、RAG、vector store、embedding、payload、messageType、SSE、Kafka 或 Redis。
                生成模式：%s
                用户需求：%s
                生成参数 JSON：
                %s
                试卷/题目蓝图 JSON：
                %s
                平台课程资料：
                %s
                检索参考资料：
                %s
                """.formatted(
                paper ? "paper" : "question_set",
                userMessage,
                toJson(request),
                toJson(blueprint),
                platformDataTool.summarize(context),
                toJson(summarizeEvidences(evidences, 8)));
        return aiProviderCallGuard.call(() -> chatClient.prompt()
                .user(prompt)
                .call()
                .content());
    }

    private Map<String, Object> parsePayload(String response) {
        if (!StringUtils.hasText(response)) {
            return Map.of("title", "AI generation result", "questions", List.of());
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(json, MAP_TYPE));
        } catch (JsonProcessingException exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("title", "AI generation result");
            fallback.put("raw", response);
            fallback.put("questions", List.of());
            return fallback;
        }
    }

    private List<Map<String, Object>> normalizeQuestions(Map<String, Object> payload,
                                                         GenerationRequest request,
                                                         boolean paper) {
        Object value = payload.get("questions");
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return fallbackQuestions(payload, request, paper);
        }
        List<Map<String, Object>> questions = new ArrayList<>();
        int index = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                questions.add(normalizeQuestion(map, request, index));
                index++;
            }
        }
        return questions.isEmpty() ? fallbackQuestions(payload, request, paper) : questions;
    }

    private List<Map<String, Object>> fallbackQuestions(Map<String, Object> payload,
                                                        GenerationRequest request,
                                                        boolean paper) {
        String raw = value(payload.get("raw"));
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionTitle", paper ? "试卷生成结果需人工整理" : "题目生成结果需人工整理");
        question.put("questionContent", StringUtils.hasText(raw) ? raw : "AI 未返回可解析的题目结构，请调整要求后重试。");
        question.put("questionType", request.questionType() == null || request.questionType() == 5 ? 4 : request.questionType());
        question.put("difficulty", request.difficulty() == null ? 2 : request.difficulty());
        question.put("score", scorePerQuestion(request));
        question.put("estimatedTime", 3);
        question.put("tags", safeList(request.knowledgePoints()));
        question.put("answers", List.of(Map.of("content", "请教师根据题干补充参考答案。", "score", scorePerQuestion(request))));
        return List.of(question);
    }

    private Map<String, Object> normalizeQuestion(Map<?, ?> source, GenerationRequest request, int index) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("id", UuidV7Generator.generate().toString());
        question.put("questionTitle", text(source, "questionTitle", "题目 " + (index + 1)));
        question.put("questionContent", text(source, "questionContent", text(source, "content", "")));
        question.put("questionType", intValue(source.get("questionType"), request.questionType() == null || request.questionType() == 5 ? 4 : request.questionType()));
        question.put("difficulty", intValue(source.get("difficulty"), request.difficulty() == null ? 2 : request.difficulty()));
        question.put("score", decimalValue(source.get("score"), scorePerQuestion(request)));
        question.put("estimatedTime", intValue(source.get("estimatedTime"), 3));
        question.put("tags", normalizeStringList(source.get("tags"), request.knowledgePoints()));
        question.put("options", normalizeOptions(source.get("options")));
        question.put("answers", normalizeAnswers(source.get("answers"), question.get("score")));
        return question;
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
                option.put("label", text(map, "label", text(map, "optionLabel", String.valueOf((char) ('A' + index)))));
                option.put("content", text(map, "content", text(map, "optionContent", "")));
                option.put("correct", booleanValue(map.get("correct"), intValue(map.get("isCorrect"), 0) == 1));
                option.put("explanation", text(map, "explanation", ""));
                options.add(option);
                index++;
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
                Map<String, Object> answer = new LinkedHashMap<>();
                answer.put("content", text(map, "content", text(map, "answerContent", "")));
                answer.put("explanation", text(map, "explanation", ""));
                answer.put("score", decimalValue(map.get("score"), decimalValue(fallbackScore, BigDecimal.ZERO)));
                answer.put("sortOrder", intValue(map.get("sortOrder"), index));
                answers.add(answer);
                index++;
            }
        }
        return answers;
    }

    private List<GeneratedQuestionDraft> draftQuestions(List<Map<String, Object>> questions) {
        List<GeneratedQuestionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            drafts.add(new GeneratedQuestionDraft(UuidV7Generator.generate().toString(), i + 1, questions.get(i), List.of()));
        }
        return drafts;
    }

    private List<GenerationValidationIssue> validateQuestions(List<Map<String, Object>> questions,
                                                              GenerationRequest request,
                                                              boolean paper) {
        List<GenerationValidationIssue> issues = new ArrayList<>();
        int expectedCount = request.questionCount() == null ? 1 : request.questionCount();
        if (questions.size() != expectedCount) {
            issues.add(new GenerationValidationIssue(
                    "QUESTION_COUNT_MISMATCH",
                    "warning",
                    "生成题目数量为 " + questions.size() + "，目标数量为 " + expectedCount + "。",
                    null,
                    "请在预览时确认是否需要补充或删减。"));
        }
        Set<String> signatures = new LinkedHashSet<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            String title = value(question.get("questionTitle"));
            String content = value(question.get("questionContent"));
            if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) {
                issues.add(new GenerationValidationIssue("EMPTY_STEM", "error", "题干为空。", i, "补充清晰题干。"));
            }
            int type = intValue(question.get("questionType"), 4);
            List<?> options = question.get("options") instanceof List<?> list ? list : List.of();
            List<?> answers = question.get("answers") instanceof List<?> list ? list : List.of();
            if (type <= 2 && options.size() < 2) {
                issues.add(new GenerationValidationIssue("OPTIONS_REQUIRED", "error", "客观题选项不足。", i, "至少提供两个选项。"));
            }
            if (answers.isEmpty()) {
                issues.add(new GenerationValidationIssue("ANSWER_REQUIRED", "warning", "缺少参考答案。", i, "补充答案和解析。"));
            }
            String signature = (title + "\n" + content).replaceAll("\\s+", "").toLowerCase();
            if (StringUtils.hasText(signature) && !signatures.add(signature)) {
                issues.add(new GenerationValidationIssue("DUPLICATE_QUESTION", "warning", "发现重复题目。", i, "替换重复题干。"));
            }
        }
        if (paper && request.totalScore() != null) {
            BigDecimal scoreSum = questions.stream()
                    .map(question -> decimalValue(question.get("score"), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (scoreSum.compareTo(request.totalScore()) != 0) {
                issues.add(new GenerationValidationIssue(
                        "TOTAL_SCORE_MISMATCH",
                        "warning",
                        "试卷总分为 " + scoreSum.stripTrailingZeros().toPlainString()
                                + "，目标总分为 " + request.totalScore().stripTrailingZeros().toPlainString() + "。",
                        null,
                        "已按默认分值展示，保存前可人工调整。"));
            }
        }
        return issues;
    }

    private List<Map<String, Object>> repairQuestions(List<Map<String, Object>> questions,
                                                      GenerationRequest request,
                                                      List<GenerationValidationIssue> issues) {
        List<Map<String, Object>> repaired = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            Map<String, Object> copy = new LinkedHashMap<>(question);
            if (!StringUtils.hasText(value(copy.get("questionTitle")))) {
                copy.put("questionTitle", "未命名题目");
            }
            if (!StringUtils.hasText(value(copy.get("questionContent")))) {
                copy.put("questionContent", copy.get("questionTitle"));
            }
            if (!(copy.get("answers") instanceof List<?> answers) || answers.isEmpty()) {
                copy.put("answers", List.of(Map.of(
                        "content", "请教师根据题干补充参考答案。",
                        "explanation", "",
                        "score", decimalValue(copy.get("score"), scorePerQuestion(request)),
                        "sortOrder", 1)));
            }
            repaired.add(copy);
        }
        return repaired;
    }

    private Map<String, Object> finalPayload(Map<String, Object> modelPayload,
                                             GenerationRequest request,
                                             PaperBlueprint blueprint,
                                             List<Map<String, Object>> finalQuestions,
                                             List<GenerationValidationIssue> issues,
                                             List<GenerationTraceEntry> traceEntries,
                                             List<AgentSearchEvent> agentSearchEvents,
                                             boolean paper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", text(modelPayload, "title", blueprint.title()));
        payload.put("type", paper ? "paper" : "question_set");
        payload.put("generation", requestPayload(request));
        payload.put("blueprint", blueprint);
        payload.put("questions", finalQuestions);
        payload.put("issues", issues);
        payload.put("generationTrace", traceEntries);
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
                Object label = map.get("label");
                Object content = map.get("content");
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
                           Map<String, Object> payload) {
        GenerationStageEvent event = GenerationStageEvent.of(requestId, mode, stage, status, title, summary, payload);
        traceEntries.add(new GenerationTraceEntry(
                UuidV7Generator.generate().toString(),
                stage,
                "question-generation",
                stage.toLowerCase(),
                title,
                summary,
                event.payload(),
                event.timestamp()));
        if (listener != null) {
            listener.accept(event);
        }
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
                "drafts", drafts,
                "questions", drafts.stream().map(GeneratedQuestionDraft::question).toList());
    }

    private Map<String, Object> validationPayload(List<Map<String, Object>> questions, List<GenerationValidationIssue> issues) {
        return Map.of(
                "questionCount", questions.size(),
                "issueCount", issues.size(),
                "questions", summarizeQuestions(questions, 6),
                "issues", issues);
    }

    private Map<String, Object> questionPayload(List<Map<String, Object>> questions, List<GenerationValidationIssue> issues) {
        return Map.of(
                "questionCount", questions.size(),
                "remainingIssueCount", issues.size(),
                "questions", summarizeQuestions(questions, 6),
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
        for (AgentSearchItem evidence : evidences) {
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

    private List<Map<String, Object>> summarizeQuestions(List<Map<String, Object>> questions, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> question : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", question.get("id"));
            item.put("questionTitle", abbreviate(value(question.get("questionTitle")), 120));
            item.put("questionContent", abbreviate(value(question.get("questionContent")), 180));
            item.put("questionType", question.get("questionType"));
            item.put("difficulty", question.get("difficulty"));
            item.put("score", question.get("score"));
            item.put("estimatedTime", question.get("estimatedTime"));
            item.put("tags", question.get("tags"));
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
        return parts.isEmpty() ? "课程知识点 题库 题目" : String.join(" ", parts);
    }

    private BigDecimal scorePerQuestion(GenerationRequest request) {
        if (request.scorePerQuestion() != null) {
            return request.scorePerQuestion();
        }
        if (request.totalScore() != null && request.questionCount() != null && request.questionCount() > 0) {
            return request.totalScore().divide(BigDecimal.valueOf(request.questionCount()), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(5);
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
}
