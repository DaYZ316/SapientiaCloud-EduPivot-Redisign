package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.entity.Conversation;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.repository.ConversationRepository;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
        assertThat(file.filename()).endsWith("-学生版.docx");
        String text = docxText(file.bytes());
        assertThat(text)
                .contains("Midterm Practice")
                .contains("Choice Section")
                .contains("1. HashMap load factor")
                .contains("A. 0.75");
        assertThat(text)
                .doesNotContain("Answer:")
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
        assertThat(file.filename()).endsWith("-教师版.pdf");
        assertThat(new String(file.bytes(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        String html = service.renderHtml(service.buildDocument(message, true));
        assertThat(html)
                .contains("参考答案与解析")
                .contains("Answer: A")
                .contains("The default load factor is 0.75");
        assertThat(html.indexOf("参考答案与解析")).isGreaterThan(html.indexOf("HashMap load factor"));
    }

    @Test
    void exportShouldCreateStudentPdfAsFormalPaperWithoutAnswers() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, paperPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "pdf", false);

        assertThat(new String(file.bytes(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String html = service.renderHtml(service.buildDocument(message, false));
        assertThat(html)
                .contains("姓名")
                .contains("班级")
                .contains("学号")
                .contains("得分")
                .contains("注意事项")
                .contains("大题")
                .contains("题量")
                .contains("满分")
                .contains("Choice Section")
                .contains("<strong>A.</strong>")
                .contains("<p>0.75</p>")
                .doesNotContain("<ol")
                .doesNotContain("参考答案与解析")
                .doesNotContain("Answer: A")
                .doesNotContain("The default load factor is 0.75");
    }

    @Test
    void exportShouldKeepQuestionSetPdfExportableWhenBlueprintMissing() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.QUESTION_SET, Map.of(
                "title", "Question Set",
                "questions", List.of(question("First question", "A"), question("Second question", "B"))));
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "pdf", false);

        assertThat(new String(file.bytes(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String html = service.renderHtml(service.buildDocument(message, false));
        assertThat(html)
                .contains("Question Set")
                .contains("题目")
                .contains("First question")
                .contains("Second question");
        assertThat(html).doesNotContain("1. A. 0.75");
    }

    @Test
    void exportShouldRenderChineseTextAndLatexImagesInPdf() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ChatMessage message = message(conversationId, AiMessageType.PAPER, chineseLatexPayload());
        AiGenerationExportService service = service(conversationId, userId, message);

        AiGenerationExportService.ExportFile file = service.export(
                conversationId, message.getId(), userId, "pdf", true);

        String text = pdfText(file.bytes());
        assertThat(text)
                .contains("Servlet")
                .doesNotContain("#")
                .doesNotContain("\\texttt");
    }

    @Test
    void exportShouldRenderLatexAsImagesInDocx() throws Exception {
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
            assertThat(document.getAllPictures()).isNotEmpty();
        }
    }

    @Test
    void buildDocumentShouldKeepQuestionSetInOriginalOrderWhenBlueprintMissing() {
        ChatMessage message = message(UUID.randomUUID(), AiMessageType.QUESTION_SET, Map.of(
                "title", "Question Set",
                "questions", List.of(question("First question", "A"), question("Second question", "B"))));
        AiGenerationExportService service = service(UUID.randomUUID(), UUID.randomUUID(), message);

        AiGenerationExportService.ExportDocument document = service.buildDocument(message, true);

        assertThat(document.sections()).hasSize(1);
        assertThat(document.sections().getFirst().questions())
                .extracting(AiGenerationExportService.ExportQuestion::title)
                .containsExactly("First question", "Second question");
    }

    @Test
    void buildDocumentShouldAcceptSchemaVersionTwoPayload() {
        ChatMessage message = message(UUID.randomUUID(), AiMessageType.QUESTION_SET, Map.of(
                "schemaVersion", 2,
                "title", "Question Set",
                "questions", List.of(question("Schema v2 question", "A"))));
        AiGenerationExportService service = service(UUID.randomUUID(), UUID.randomUUID(), message);

        AiGenerationExportService.ExportDocument document = service.buildDocument(message, true);

        assertThat(document.title()).isEqualTo("Question Set");
        assertThat(document.sections().getFirst().questions())
                .extracting(AiGenerationExportService.ExportQuestion::title)
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
        try (PDDocument document = PDDocument.load(bytes)) {
            return new PDFTextStripper().getText(document);
        }
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
                new LatexImageRenderer(),
                new ExportFontResolver());
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
                "questions", List.of(question("HashMap load factor", "A")));
    }

    private static Map<String, Object> chineseLatexPayload() {
        return Map.of(
                "title", "中文试卷",
                "generation", Map.of("paperType", "Quiz", "totalScore", 10, "totalEstimatedTime", 5),
                "blueprint", Map.of(
                        "sections", List.of(Map.of(
                                "sectionNo", 1,
                                "sectionTitle", "基础题",
                                "targetCount", 1))),
                "questions", List.of(Map.of(
                        "questionTitle", "Servlet 生命周期",
                        "questionContent", "方法 $\\texttt{init()}$ 的作用是什么？",
                        "questionType", 0,
                        "difficulty", 2,
                        "score", 5,
                        "estimatedTime", 3,
                        "options", List.of(
                                Map.of("optionLabel", "A", "optionContent", "初始化方法 $\\texttt{init()}$", "isCorrect", true),
                                Map.of("optionLabel", "B", "optionContent", "销毁方法 $\\texttt{destroy()}$", "isCorrect", false)),
                        "answers", List.of(Map.of("answerContent", "答案：A")),
                        "explanation", "解析：$\\texttt{init()}$ 在实例创建后调用。")));
    }

    private static Map<String, Object> question(String title, String answerLabel) {
        return Map.of(
                "questionTitle", title,
                "questionContent", "Choose the correct answer.",
                "questionType", 0,
                "difficulty", 2,
                "score", 5,
                "estimatedTime", 3,
                "options", List.of(
                        Map.of("optionLabel", "A", "optionContent", "0.75", "isCorrect", "A".equals(answerLabel)),
                        Map.of("optionLabel", "B", "optionContent", "1.0", "isCorrect", "B".equals(answerLabel))),
                "answers", List.of(Map.of("answerContent", "Answer: " + answerLabel)),
                "explanation", "The default load factor is 0.75");
    }
}
