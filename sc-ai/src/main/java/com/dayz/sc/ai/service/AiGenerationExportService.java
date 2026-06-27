package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AiGenerationExportService.
 *
 * @author DaYZ
 */
@Service
@RequiredArgsConstructor
public class AiGenerationExportService {

    private static final String DEFAULT_PAPER_NAME = "AI生成试卷";

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final QuestionPaperExportFormatter exportFormatter;

    private static Object firstNonEmpty(Object first, Object second) {
        return StringUtils.hasText(displayValue(first)) ? first : second;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = displayValue(value);
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return "";
    }

    private static String displayValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text) || "1".equals(text);
        }
        return false;
    }

    private static List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            String text = displayValue(item);
            if (StringUtils.hasText(text)) {
                result.add(text.trim());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    public ExportFile export(UUID conversationId, UUID messageId, UUID userId, String format, boolean includeAnswers) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        conversationService.requireOwnedConversation(conversationId, userId);
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        if (!conversationId.equals(message.getConversationId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Message does not belong to the conversation");
        }

        QuestionPaperExportRequestDTO request = buildExportRequest(message, includeAnswers);
        QuestionPaperExportFormatter.ExportedPaperFile file = switch (exportFormat) {
            case PDF -> exportFormatter.exportPdf(request);
            case DOCX -> exportFormatter.exportWord(request);
        };
        return new ExportFile(file.fileName(), file.contentType(), file.content());
    }

    QuestionPaperExportRequestDTO buildExportRequest(ChatMessage message, boolean includeAnswers) {
        if (!AiMessageType.PAPER.name().equals(message.getMessageType())
                && !AiMessageType.QUESTION_SET.name().equals(message.getMessageType())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Only generated questions or papers can be exported");
        }
        Map<String, Object> payload = message.getPayload();
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Generated content is missing");
        }

        List<QuestionResponseDTO> questions = exportQuestions(payload.get("questions"));
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Generated questions are missing");
        }

        QuestionPaperExportRequestDTO request = new QuestionPaperExportRequestDTO();
        request.setPaperName(firstText(payload.get("title"), mapValue(payload.get("generation")).get("paperName"), DEFAULT_PAPER_NAME));
        request.setQuestions(questions);
        request.setIncludeAnswers(includeAnswers);
        return request;
    }

    private List<QuestionResponseDTO> exportQuestions(Object value) {
        List<QuestionResponseDTO> questions = new ArrayList<>();
        for (Map<String, Object> item : mapList(value)) {
            questions.add(exportQuestion(item));
        }
        return questions;
    }

    private QuestionResponseDTO exportQuestion(Map<String, Object> question) {
        QuestionResponseDTO exportQuestion = new QuestionResponseDTO();
        String title = firstText(question.get("questionTitle"), question.get("title"));
        String content = firstText(
                question.get("questionContent"),
                question.get("content"),
                question.get("questionStem"),
                question.get("stem"));
        boolean hasExplicitContent = StringUtils.hasText(content);
        exportQuestion.setQuestionTitle(hasExplicitContent && !title.equals(content) ? title : "");
        exportQuestion.setQuestionContent(hasExplicitContent ? content : title);
        exportQuestion.setQuestionType(integerValue(firstNonEmpty(question.get("questionType"), question.get("type"))));
        exportQuestion.setDifficulty(integerValue(question.get("difficulty")));
        exportQuestion.setScore(decimalValue(question.get("score")));
        exportQuestion.setEstimatedTime(integerValue(question.get("estimatedTime")));
        exportQuestion.setTags(stringListValue(question.get("tags")));
        exportQuestion.setOptions(exportOptions(question.get("options")));
        exportQuestion.setAnswers(exportAnswers(question.get("answers"), question));
        return exportQuestion;
    }

    private List<QuestionOptionSimpleDTO> exportOptions(Object value) {
        List<QuestionOptionSimpleDTO> options = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> item : mapList(value)) {
            QuestionOptionSimpleDTO option = new QuestionOptionSimpleDTO();
            option.setOptionLabel(firstText(item.get("optionLabel"), item.get("label"), String.valueOf((char) ('A' + index))));
            option.setOptionContent(firstText(item.get("optionContent"), item.get("content"), item.get("answerContent")));
            option.setIsCorrect(isTruthy(firstNonEmpty(item.get("isCorrect"), item.get("correct"))) ? 1 : 0);
            option.setScore(decimalValue(item.get("score")));
            option.setImageUrls(stringListValue(item.get("imageUrls")));
            option.setExplanation(firstText(item.get("explanation")));
            options.add(option);
            index++;
        }
        return options;
    }

    private List<QuestionAnswerSimpleDTO> exportAnswers(Object value, Map<String, Object> question) {
        List<QuestionAnswerSimpleDTO> answers = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> item : mapList(value)) {
            QuestionAnswerSimpleDTO answer = new QuestionAnswerSimpleDTO();
            answer.setAnswerContent(firstText(item.get("answerContent"), item.get("content")));
            answer.setExplanation(firstText(item.get("explanation")));
            answer.setScore(decimalValue(item.get("score")));
            answer.setSortOrder(integerValue(firstNonEmpty(item.get("sortOrder"), index + 1)));
            answers.add(answer);
            index++;
        }
        String explanation = firstText(question.get("explanation"), question.get("answerExplanation"));
        if (StringUtils.hasText(explanation)) {
            if (answers.isEmpty()) {
                QuestionAnswerSimpleDTO answer = new QuestionAnswerSimpleDTO();
                answer.setAnswerContent(firstText(question.get("answer"), question.get("correctAnswer")));
                answer.setExplanation(explanation);
                answer.setSortOrder(1);
                answers.add(answer);
            } else if (!StringUtils.hasText(answers.getFirst().getExplanation())) {
                answers.getFirst().setExplanation(explanation);
            }
        }
        return answers;
    }

    /**
     * 导出格式
     */
    private enum ExportFormat {

        /**
         * PDF 格式
         */
        PDF("pdf"),

        /**
         * Word 格式
         */
        DOCX("docx");

        private final String value;

        ExportFormat(String value) {
            this.value = value;
        }

        private static ExportFormat parse(String value) {
            for (ExportFormat format : values()) {
                if (format.value.equalsIgnoreCase(value)) {
                    return format;
                }
            }
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Unsupported export format");
        }
    }

    public record ExportFile(String filename, String contentType, byte[] bytes) {
    }
}
