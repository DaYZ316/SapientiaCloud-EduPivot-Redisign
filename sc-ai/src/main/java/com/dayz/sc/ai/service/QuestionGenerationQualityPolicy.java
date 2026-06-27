package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.dto.GenerationRequest;
import com.dayz.sc.ai.model.vo.GenerationValidationIssue;
import com.dayz.sc.ai.model.vo.PaperBlueprint;
import com.dayz.sc.ai.model.vo.PaperSectionPlan;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

final class QuestionGenerationQualityPolicy {

    private static final int QUESTION_TYPE_SINGLE_CHOICE = 0;
    private static final int QUESTION_TYPE_MULTI_CHOICE = 1;
    private static final int QUESTION_TYPE_TRUE_FALSE = 2;
    private static final int QUESTION_TYPE_FILL_BLANK = 3;
    private static final int QUESTION_TYPE_SHORT_ANSWER = 4;
    private static final int TWO_QUESTION_MIXED_COUNT = 2;
    private static final int THREE_QUESTION_MIXED_COUNT = 3;
    private static final int BASE_MIXED_TYPE_COUNT = 4;
    private static final int MIN_PAPER_QUESTION_COUNT = 5;
    private static final int MIN_MULTI_SELECT_OPTION_COUNT = 4;
    private static final int MIN_TOPIC_DIVERSITY_COUNT = 2;
    private static final String KEY_TAGS = "tags";
    private static final List<Integer> PAPER_MIXED_BASE_TYPES = List.of(
            QUESTION_TYPE_SINGLE_CHOICE,
            QUESTION_TYPE_MULTI_CHOICE,
            QUESTION_TYPE_FILL_BLANK,
            QUESTION_TYPE_SHORT_ANSWER);
    private static final Set<String> REPAIRABLE_CODES = Set.of(
            "PAPER_OBJECTIVE_ONLY",
            "WEAK_MULTI_SELECT_DISTRACTOR",
            "LOW_TOPIC_DIVERSITY",
            "OBJECTIVE_ANSWER_CONFLICT");

    private QuestionGenerationQualityPolicy() {
    }

    static List<Integer> paperMixedQuestionTypes(int totalCount) {
        if (totalCount <= 0) {
            return List.of();
        }
        if (totalCount == 1) {
            return List.of(QUESTION_TYPE_SHORT_ANSWER);
        }
        if (totalCount == TWO_QUESTION_MIXED_COUNT) {
            return List.of(QUESTION_TYPE_SINGLE_CHOICE, QUESTION_TYPE_SHORT_ANSWER);
        }
        if (totalCount == THREE_QUESTION_MIXED_COUNT) {
            return List.of(QUESTION_TYPE_SINGLE_CHOICE, QUESTION_TYPE_MULTI_CHOICE, QUESTION_TYPE_SHORT_ANSWER);
        }
        if (totalCount == BASE_MIXED_TYPE_COUNT) {
            return PAPER_MIXED_BASE_TYPES;
        }
        int judgeCount = Math.max(1, Math.round(totalCount * 0.1f));
        int nonJudgeCount = Math.max(0, totalCount - judgeCount);
        List<Integer> types = new ArrayList<>(totalCount);
        for (int i = 0; i < nonJudgeCount; i++) {
            types.add(PAPER_MIXED_BASE_TYPES.get(i % PAPER_MIXED_BASE_TYPES.size()));
        }
        for (int i = 0; i < judgeCount; i++) {
            types.add(QUESTION_TYPE_TRUE_FALSE);
        }
        return types;
    }

    static long scoreWeight(int questionType, int difficulty) {
        BigDecimal typeWeight = switch (questionType) {
            case QUESTION_TYPE_SINGLE_CHOICE -> BigDecimal.valueOf(1.0);
            case QUESTION_TYPE_MULTI_CHOICE -> BigDecimal.valueOf(1.2);
            case QUESTION_TYPE_TRUE_FALSE -> BigDecimal.valueOf(0.5);
            case QUESTION_TYPE_FILL_BLANK -> BigDecimal.valueOf(1.4);
            case QUESTION_TYPE_SHORT_ANSWER -> BigDecimal.valueOf(2.0);
            default -> BigDecimal.ONE;
        };
        BigDecimal difficultyWeight = switch (difficulty) {
            case 1 -> BigDecimal.valueOf(1.0);
            case 3 -> BigDecimal.valueOf(1.5);
            default -> BigDecimal.valueOf(1.25);
        };
        return Math.max(1L, typeWeight.multiply(difficultyWeight).multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue());
    }

    static QualityReview review(List<Map<String, Object>> questions,
                                @Nullable GenerationRequest request,
                                @Nullable PaperBlueprint blueprint,
                                boolean paper) {
        List<Map<String, Object>> safeQuestions = questions == null ? List.of() : questions;
        List<GenerationValidationIssue> issues = new ArrayList<>();
        if (paper) {
            appendPaperIssues(issues, safeQuestions, request, blueprint);
        }
        appendQuestionQualityIssues(issues, safeQuestions, request);
        return new QualityReview(issues, payload(safeQuestions, request, blueprint, issues));
    }

    static boolean isRepairable(GenerationValidationIssue issue) {
        return issue != null && REPAIRABLE_CODES.contains(issue.code());
    }

    static GenerationValidationIssue objectiveAnswerConflictIssue(int questionIndex, String expected, String actual) {
        return new GenerationValidationIssue(
                "OBJECTIVE_ANSWER_CONFLICT",
                "warning",
                "第 " + questionIndex + " 题客观题答案字段与选项正确标记不一致，已按 options.isCorrect 作为保存答案。",
                questionIndex,
                "请让模型重写该题，确保 answers 与 options.isCorrect 表达同一答案。"
                        + " 选项标记为 " + expected + "，answers 写的是 " + actual + "。");
    }

    private static void appendPaperIssues(List<GenerationValidationIssue> issues,
                                          List<Map<String, Object>> questions,
                                          @Nullable GenerationRequest request,
                                          @Nullable PaperBlueprint blueprint) {
        int targetCount = blueprint == null ? questions.size() : blueprint.totalQuestionCount();
        if (request != null && request.questionCount() != null && targetCount > 0 && targetCount < MIN_PAPER_QUESTION_COUNT) {
            issues.add(new GenerationValidationIssue(
                    "PAPER_TOO_FEW_QUESTIONS",
                    "warning",
                    "试卷题量只有 " + targetCount + " 道，难以形成稳定的测评结果。",
                    null,
                    "建议将试卷题量提高到至少 5 道，或将本次结果作为专题小测使用。"));
        }
        long objectiveCount = questions.stream()
                .filter(question -> questionType(question) <= QUESTION_TYPE_TRUE_FALSE)
                .count();
        if (questions.size() >= QUESTION_TYPE_FILL_BLANK && objectiveCount == questions.size()) {
            issues.add(new GenerationValidationIssue(
                    "PAPER_OBJECTIVE_ONLY",
                    "warning",
                    "当前试卷全部为客观题，缺少可考查推理过程或表达能力的题型。",
                    null,
                    "请至少替换一道题为填空题或简答题。"));
        }
    }

    private static void appendQuestionQualityIssues(List<GenerationValidationIssue> issues,
                                                    List<Map<String, Object>> questions,
                                                    @Nullable GenerationRequest request) {
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            int index = i + 1;
            int type = questionType(question);
            BigDecimal score = decimalValue(question.get("score"), BigDecimal.ZERO);
            BigDecimal threshold = scoreWarningThreshold(type);
            if (threshold != null && score.compareTo(threshold) > 0) {
                issues.add(highScoreIssue(type, index, score, threshold));
            }
            if (type == QUESTION_TYPE_MULTI_CHOICE && isWeakMultiSelect(question)) {
                issues.add(new GenerationValidationIssue(
                        "WEAK_MULTI_SELECT_DISTRACTOR",
                        "warning",
                        "第 " + index + " 题多选题正确项比例过高，干扰项区分度偏弱。",
                        index,
                        "请增加至少两个有迷惑性但明确错误的干扰项，避免 4 个选项中 3 个正确。"));
            }
        }
        appendTopicDiversityIssue(issues, questions, request);
    }

    private static GenerationValidationIssue highScoreIssue(int type,
                                                            int index,
                                                            BigDecimal score,
                                                            BigDecimal threshold) {
        String code = switch (type) {
            case QUESTION_TYPE_SINGLE_CHOICE -> "SINGLE_SCORE_TOO_HIGH";
            case QUESTION_TYPE_MULTI_CHOICE -> "MULTI_SCORE_TOO_HIGH";
            case QUESTION_TYPE_TRUE_FALSE -> "JUDGE_SCORE_TOO_HIGH";
            case QUESTION_TYPE_FILL_BLANK -> "BLANK_SCORE_TOO_HIGH";
            case QUESTION_TYPE_SHORT_ANSWER -> "SHORT_SCORE_TOO_HIGH";
            default -> "QUESTION_SCORE_TOO_HIGH";
        };
        return new GenerationValidationIssue(
                code,
                "warning",
                "第 " + index + " 题分值 " + plain(score) + " 超过该题型建议上限 " + plain(threshold) + "。",
                index,
                "请检查总分或题型结构；如总分为用户显式要求，可保留但需人工确认。");
    }

    private static boolean isWeakMultiSelect(Map<String, Object> question) {
        List<Map<String, Object>> options = mapList(question.get("options"));
        if (options.size() < MIN_MULTI_SELECT_OPTION_COUNT) {
            return false;
        }
        long correctCount = options.stream()
                .filter(option -> intValue(option.get("isCorrect"), 0) == 1)
                .count();
        return correctCount >= options.size() - 1L;
    }

    private static void appendTopicDiversityIssue(List<GenerationValidationIssue> issues,
                                                  List<Map<String, Object>> questions,
                                                  @Nullable GenerationRequest request) {
        List<String> requested = request == null ? List.of() : normalizeTerms(request.knowledgePoints());
        if (requested.size() < MIN_TOPIC_DIVERSITY_COUNT || questions.size() < MIN_TOPIC_DIVERSITY_COUNT) {
            return;
        }
        Set<String> covered = new LinkedHashSet<>();
        for (Map<String, Object> question : questions) {
            for (String tag : normalizeTerms(question.get(KEY_TAGS))) {
                if (requested.contains(tag)) {
                    covered.add(tag);
                }
            }
        }
        int expectedCoverage = Math.min(requested.size(), questions.size());
        if (covered.size() < expectedCoverage) {
            issues.add(new GenerationValidationIssue(
                    "LOW_TOPIC_DIVERSITY",
                    "warning",
                    "题目知识点覆盖不足，用户提供 " + requested.size() + " 个知识点，当前仅覆盖 " + covered.size() + " 个。",
                    null,
                    "请围绕未覆盖知识点补充或改写题目，避免整卷集中在单一知识点。"));
        }
    }

    private static Map<String, Object> payload(List<Map<String, Object>> questions,
                                               @Nullable GenerationRequest request,
                                               @Nullable PaperBlueprint blueprint,
                                               List<GenerationValidationIssue> issues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionCount", questions.size());
        payload.put("issueCount", issues.size());
        payload.put("issues", issues);
        payload.put("questionTypes", questionTypeDistribution(questions));
        payload.put("scores", scoreDistribution(questions));
        payload.put("estimatedTimes", estimatedTimeDistribution(questions));
        payload.put("knowledgePoints", knowledgePointCoverage(questions, request));
        if (blueprint != null) {
            payload.put("blueprintSections", blueprint.sections().stream()
                    .map(QuestionGenerationQualityPolicy::sectionPayload)
                    .toList());
        }
        return payload;
    }

    private static Map<String, Object> sectionPayload(PaperSectionPlan section) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sectionNo", section.sectionNo());
        payload.put("questionType", section.questionType());
        payload.put("targetCount", section.targetCount());
        payload.put("scorePerQuestion", section.scorePerQuestion());
        payload.put("estimatedTimePerQuestion", section.estimatedTimePerQuestion());
        return payload;
    }

    private static Map<String, Long> questionTypeDistribution(List<Map<String, Object>> questions) {
        return questions.stream()
                .collect(Collectors.groupingBy(
                        question -> String.valueOf(questionType(question)),
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    private static Map<String, Object> scoreDistribution(List<Map<String, Object>> questions) {
        BigDecimal total = questions.stream()
                .map(question -> decimalValue(question.get("score"), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("values", questions.stream().map(question -> question.get("score")).toList());
        return payload;
    }

    private static Map<String, Object> estimatedTimeDistribution(List<Map<String, Object>> questions) {
        int total = questions.stream().mapToInt(question -> intValue(question.get("estimatedTime"), 0)).sum();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("values", questions.stream().map(question -> question.get("estimatedTime")).toList());
        return payload;
    }

    private static Map<String, Object> knowledgePointCoverage(List<Map<String, Object>> questions,
                                                              @Nullable GenerationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<String> requested = request == null ? List.of() : normalizeTerms(request.knowledgePoints());
        Set<String> generated = new LinkedHashSet<>();
        for (Map<String, Object> question : questions) {
            generated.addAll(normalizeTerms(question.get("tags")));
        }
        payload.put("requested", requested);
        payload.put("generated", generated);
        return payload;
    }

    private static BigDecimal scoreWarningThreshold(int type) {
        return switch (type) {
            case 0 -> BigDecimal.valueOf(10);
            case 1, 3 -> BigDecimal.valueOf(15);
            case 2 -> BigDecimal.valueOf(5);
            case 4 -> BigDecimal.valueOf(25);
            default -> null;
        };
    }

    private static int questionType(Map<String, Object> question) {
        return intValue(question.get("questionType"), 4);
    }

    private static List<String> normalizeTerms(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(item -> item != null && StringUtils.hasText(item.toString()))
                .map(item -> Normalizer.normalize(item.toString().strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFKC))
                .distinct()
                .toList();
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        copy.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                values.add(copy);
            }
        }
        return values;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || !StringUtils.hasText(value.toString())) {
            return fallback;
        }
        try {
            return new BigDecimal(value.toString().strip()).intValue();
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null || !StringUtils.hasText(value.toString())) {
            return fallback;
        }
        try {
            return new BigDecimal(value.toString().strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String plain(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    record QualityReview(List<GenerationValidationIssue> issues, Map<String, Object> payload) {
    }
}
