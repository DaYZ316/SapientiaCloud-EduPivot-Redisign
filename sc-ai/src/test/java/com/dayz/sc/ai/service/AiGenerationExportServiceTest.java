package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.entity.Conversation;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.repository.ConversationRepository;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiGenerationExportServiceTest {

    @Test
    void exportShouldCreateDocxWithoutAnswersWhenDisabled() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, paperPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "docx", false);

        assertThat(file.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(file.filename()).isEqualTo("Midterm Practice.docx");
        String text = docxText(file.bytes());
        assertThat(text)
                .contains("Midterm Practice")
                .contains("题目数：1")
                .contains("总分：5")
                .contains("预计时长：3分钟")
                .contains("导出版本：试题版")
                .contains("第1部分 单选题（共1题，5分）")
                .contains("1. HashMap load factor")
                .contains("A. 0.75");
        assertThat(text)
                .doesNotContain("参考答案与解析")
                .doesNotContain("正确答案：A")
                .doesNotContain("The default load factor is 0.75");
    }

    @Test
    void exportShouldCreatePdfWithAnswersWhenEnabled() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, paperPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "pdf", true);

        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.filename()).isEqualTo("Midterm Practice.pdf");
        assertThat(new String(file.bytes(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        String text = compact(pdfText(file.bytes()));
        assertThat(text)
                .contains("MidtermPractice")
                .contains("题目数:1总分:5预计时长:3分钟导出版本:答案版")
                .contains("第1部分单选题(共1题,5分)")
                .contains("参考答案与解析")
                .contains("正确答案:A")
                .contains("A解析:0.75istheusualdefault")
                .contains("解析:Thedefaultloadfactoris0.75");
        assertThat(text.indexOf("参考答案与解析")).isGreaterThan(text.indexOf("HashMaploadfactor"));
    }

    @Test
    void exportShouldCreatePdfWithoutAnswersWhenDisabled() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, paperPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "pdf", false);

        String text = compact(pdfText(file.bytes()));
        assertThat(file.filename()).isEqualTo("Midterm Practice.pdf");
        assertThat(text)
                .contains("导出版本:试题版")
                .contains("第1部分单选题(共1题,5分)")
                .doesNotContain("参考答案与解析")
                .doesNotContain("正确答案:A")
                .doesNotContain("Thedefaultloadfactoris0.75");
    }

    @Test
    void exportShouldKeepTitleOnlyPayloadQuestionAsStemInPdf() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, Map.of(
                "title", "Title Only Paper",
                "questions", List.of(Map.of(
                        "questionTitle", "阅读下面材料，回答 Java 中 HashMap 默认负载因子是多少？",
                        "questionType", 0,
                        "difficulty", 2,
                        "score", 5,
                        "estimatedTime", 3,
                        "options", List.of(
                                Map.of("optionLabel", "A", "optionContent", "0.75", "isCorrect", true),
                                Map.of("optionLabel", "B", "optionContent", "1.0", "isCorrect", false))))));
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "pdf", false);

        String text = compact(pdfText(file.bytes()));
        assertThat(text)
                .contains("1.(难度:中等,分值:5,预计:3分钟)阅读下面材料,回答Java中HashMap默认负载因子是多少?")
                .doesNotContain("1.阅读下面材料");
    }

    @Test
    void exportShouldGroupMixedQuestionTypesInOldProjectOrder() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, Map.of(
                "title", "Mixed Paper",
                "questions", List.of(
                        question("Other question", 99, "A"),
                        question("Short answer question", 4, "A"),
                        question("Single choice question", 0, "A"),
                        question("Fill blank question", 3, "A"),
                        question("True false question", 2, "A"),
                        question("Multiple choice question", 1, "A"))));
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "docx", false);

        String text = docxText(file.bytes());
        assertThat(text.indexOf("第1部分 单选题")).isLessThan(text.indexOf("第2部分 多选题"));
        assertThat(text.indexOf("第2部分 多选题")).isLessThan(text.indexOf("第3部分 判断题"));
        assertThat(text.indexOf("第3部分 判断题")).isLessThan(text.indexOf("第4部分 填空题"));
        assertThat(text.indexOf("第4部分 填空题")).isLessThan(text.indexOf("第5部分 简答题"));
        assertThat(text.indexOf("第5部分 简答题")).isLessThan(text.indexOf("第6部分 其他题型"));
    }

    @Test
    void exportShouldRenderChineseTextAndLatexImagesInPdf() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, chineseLatexPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "pdf", true);

        String text = compact(pdfText(file.bytes()));
        assertThat(text)
                .contains("中文试卷")
                .contains("Servlet生命周期")
                .contains("方法")
                .doesNotContain("\\texttt");
    }

    @Test
    void exportShouldPreferOmmlAndFallbackToFormulaImageInDocx() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, chineseLatexPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "docx", true);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(file.bytes()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            assertThat(extractor.getText())
                    .contains("中文试卷")
                    .contains("Servlet 生命周期")
                    .doesNotContain("\\texttt");
            assertThat(document.getDocument().xmlText()).contains("oMath");
            assertThat(document.getAllPictures()).isNotEmpty();
        }
    }

    @Test
    void buildExportRequestShouldKeepQuestionSetPayloadOrder() {
        ChatMessage message = message(UUID.randomUUID(), AiMessageType.QUESTION_SET, Map.of(
                "title", "Question Set",
                "questions", List.of(question("First question", 0, "A"), question("Second question", 0, "B"))));
        AiGenerationExportService service = service(UUID.randomUUID(), UUID.randomUUID(), message);

        QuestionPaperExportRequestDTO request = service.buildExportRequest(message, true);

        assertThat(request.getPaperName()).isEqualTo("Question Set");
        assertThat(request.getIncludeAnswers()).isTrue();
        assertThat(request.getQuestions())
                .extracting(QuestionResponseDTO::getQuestionTitle)
                .containsExactly("First question", "Second question");
    }

    @Test
    void buildExportRequestShouldAcceptSchemaVersionTwoPayload() {
        ChatMessage message = message(UUID.randomUUID(), AiMessageType.QUESTION_SET, Map.of(
                "schemaVersion", 2,
                "title", "Question Set",
                "questions", List.of(question("Schema v2 question", 0, "A"))));
        AiGenerationExportService service = service(UUID.randomUUID(), UUID.randomUUID(), message);

        QuestionPaperExportRequestDTO request = service.buildExportRequest(message, true);

        assertThat(request.getPaperName()).isEqualTo("Question Set");
        assertThat(request.getQuestions())
                .extracting(QuestionResponseDTO::getQuestionTitle)
                .containsExactly("Schema v2 question");
    }

    @Test
    void exportShouldRejectUnsupportedFormat() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, paperPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        assertThatThrownBy(() -> service.export(conversationId, message.getId(), userId, "xlsx", false))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCodes.BAD_REQUEST.code()));
    }

    @Test
    void exportShouldRejectTextMessage() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.TEXT, paperPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        assertThatThrownBy(() -> service.export(conversationId, message.getId(), userId, "docx", false))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCodes.BAD_REQUEST.code()));
    }

    @Test
    void exportShouldRejectEmptyQuestions() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, Map.of(
                "title", "Empty Paper",
                "questions", List.of()));
        AiGenerationExportService service = service(conversationId, userId, message);

        assertThatThrownBy(() -> service.export(conversationId, message.getId(), userId, "docx", false))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCodes.BAD_REQUEST.code()));
    }

    @Test
    void exportShouldRejectMessageFromAnotherConversation() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(UUID.randomUUID(), AiMessageType.PAPER, paperPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        assertThatThrownBy(() -> service.export(conversationId, message.getId(), userId, "docx", false))
                .isInstanceOfSatisfying(BusinessException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCodes.BAD_REQUEST.code()));
    }

    private static String docxText(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String pdfText(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String compact(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('⻓', '长')
                .replaceAll("\\s+", "");
    }

    private static AiGenerationExportService service(UUID conversationId, UUID userId, ChatMessage message) {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        ConversationService conversationService = new ConversationService(
                conversationRepository,
                messageRepository,
                mock(ChatVectorMemoryService.class));
        when(conversationRepository.findByIdAndUserId(conversationId, userId))
                .thenReturn(Optional.of(new Conversation()));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        return new AiGenerationExportService(
                conversationService,
                messageRepository,
                new QuestionPaperExportFormatter());
    }

    private static ChatMessage message(UUID conversationId, AiMessageType messageType, Map<String, Object> payload) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setConversationId(conversationId);
        message.setMessageType(messageType.name());
        message.setPayload(payload);
        return message;
    }

    private static Map<String, Object> paperPayload() {
        return Map.of(
                "title", "Midterm Practice",
                "generation", Map.of("paperType", "Quiz", "totalScore", 100, "totalEstimatedTime", 60),
                "blueprint", Map.of(
                        "totalScore", 100,
                        "totalEstimatedTime", 60,
                        "sections", List.of(Map.of(
                                "sectionNo", 1,
                                "sectionTitle", "Choice Section",
                                "targetCount", 1))),
                "questions", List.of(question("HashMap load factor", 0, "A")));
    }

    private static Map<String, Object> chineseLatexPayload() {
        return Map.of(
                "title", "中文试卷",
                "questions", List.of(Map.of(
                        "questionTitle", "Servlet 生命周期",
                        "questionContent", "方法 $x^2$ 与 $\\texttt{init()}$ 的作用是什么？",
                        "questionType", 0,
                        "difficulty", 2,
                        "score", 5,
                        "estimatedTime", 3,
                        "options", List.of(
                                Map.of("optionLabel", "A", "optionContent", "初始化方法 $x^2$", "isCorrect", true),
                                Map.of("optionLabel", "B", "optionContent", "销毁方法 $\\texttt{destroy()}$", "isCorrect", false)),
                        "answers", List.of(Map.of("answerContent", "A")),
                        "explanation", "$\\texttt{init()}$ 在实例创建后调用。")));
    }

    private static Map<String, Object> question(String title, Integer type, String answerLabel) {
        return Map.of(
                "questionTitle", title,
                "questionContent", "Choose the correct answer.",
                "questionType", type,
                "difficulty", 2,
                "score", 5,
                "estimatedTime", 3,
                "options", List.of(
                        Map.of(
                                "optionLabel", "A",
                                "optionContent", "0.75",
                                "isCorrect", "A".equals(answerLabel),
                                "explanation", "0.75 is the usual default"),
                        Map.of("optionLabel", "B", "optionContent", "1.0", "isCorrect", "B".equals(answerLabel))),
                "answers", List.of(Map.of("answerContent", answerLabel)),
                "explanation", "The default load factor is 0.75");
    }
}
