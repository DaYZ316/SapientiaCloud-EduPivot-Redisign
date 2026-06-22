package com.dayz.sc.ai.service;

import com.dayz.sc.ai.model.entity.ChatMessage;
import com.dayz.sc.ai.model.enums.AiMessageType;
import com.dayz.sc.ai.repository.MessageRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiGenerationExportService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DOCX_FONT_FAMILY = "Microsoft YaHei";
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Pattern ORDERED_ITEM_PATTERN = Pattern.compile("^(\\d+[.)])\\s+(.+)$");
    private static final Pattern UNORDERED_ITEM_PATTERN = Pattern.compile("^[-*+]\\s+(.+)$");
    private static final Pattern FENCED_CODE_PATTERN = Pattern.compile("^(```|~~~).*");
    private static final Pattern BARE_LATEX_BLOCK_PATTERN = Pattern.compile(
            "^\\\\begin\\{(aligned|alignedat|gathered|cases|array|matrix|pmatrix|bmatrix|vmatrix|Vmatrix|split|align\\*?|gather\\*?|equation\\*?)\\}");

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;
    private final LatexImageRenderer latexImageRenderer;
    private final ExportFontResolver exportFontResolver;

    public ExportFile export(UUID conversationId, UUID messageId, UUID userId, String format, boolean includeAnswers) {
        ExportFormat exportFormat = ExportFormat.parse(format);
        conversationService.requireOwnedConversation(conversationId, userId);
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        if (!conversationId.equals(message.getConversationId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Message does not belong to the conversation");
        }

        ExportDocument document = buildDocument(message, includeAnswers);
        byte[] bytes = switch (exportFormat) {
            case PDF -> renderPdf(document);
            case DOCX -> renderDocx(document);
        };
        return new ExportFile(
                exportFilename(document.title(), includeAnswers, exportFormat.extension),
                exportFormat.contentType,
                bytes);
    }

    ExportDocument buildDocument(ChatMessage message, boolean includeAnswers) {
        if (!AiMessageType.PAPER.name().equals(message.getMessageType())
                && !AiMessageType.QUESTION_SET.name().equals(message.getMessageType())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Only generated questions or papers can be exported");
        }
        Map<String, Object> payload = message.getPayload();
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Generated content is missing");
        }

        List<ExportQuestion> questions = exportQuestions(payload.get("questions"));
        if (questions.isEmpty()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Generated questions are missing");
        }

        String fallbackTitle = AiMessageType.PAPER.name().equals(message.getMessageType())
                ? "AI 出卷结果"
                : "AI 出题结果";
        return new ExportDocument(
                firstText(payload.get("title"), fallbackTitle),
                documentMetadata(payload, questions.size()),
                firstText(mapValue(payload.get("generation")).get("requirement"), payload.get("requirement")),
                exportSections(payload.get("blueprint"), questions),
                includeAnswers);
    }

    private List<ExportQuestion> exportQuestions(Object value) {
        List<ExportQuestion> questions = new ArrayList<>();
        int number = 1;
        for (Map<String, Object> item : mapList(value)) {
            questions.add(exportQuestion(number++, item));
        }
        return questions;
    }

    private ExportQuestion exportQuestion(int number, Map<String, Object> question) {
        List<ExportOption> options = exportOptions(question.get("options"));
        return new ExportQuestion(
                number,
                firstText(question.get("questionTitle"), question.get("title"), "未命名题目"),
                firstText(question.get("questionContent"), question.get("content"), question.get("questionTitle")),
                firstText(question.get("score")),
                questionMetadata(question),
                options,
                answerItems(question, options),
                explanationText(question, options));
    }

    private List<ExportOption> exportOptions(Object value) {
        List<ExportOption> options = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> item : mapList(value)) {
            options.add(new ExportOption(
                    firstText(item.get("optionLabel"), item.get("label"), String.valueOf((char) ('A' + index))),
                    firstText(item.get("optionContent"), item.get("content"), item.get("answerContent")),
                    isTruthy(item.get("isCorrect")) || isTruthy(item.get("correct")),
                    firstText(item.get("explanation"))));
            index++;
        }
        return options;
    }

    private List<String> answerItems(Map<String, Object> question, List<ExportOption> options) {
        List<String> answers = new ArrayList<>();
        for (Map<String, Object> item : mapList(question.get("answers"))) {
            String answer = firstText(item.get("answerContent"), item.get("content"));
            if (StringUtils.hasText(answer)) {
                answers.add(answer);
            }
        }
        if (!answers.isEmpty()) {
            return answers;
        }

        return options.stream()
                .filter(ExportOption::correct)
                .map(option -> option.label() + ". " + option.content())
                .toList();
    }

    private String explanationText(Map<String, Object> question, List<ExportOption> options) {
        String explanation = firstText(question.get("explanation"), question.get("answerExplanation"));
        if (StringUtils.hasText(explanation)) {
            return explanation;
        }
        for (ExportOption option : options) {
            if (option.correct() && StringUtils.hasText(option.explanation())) {
                return option.explanation();
            }
        }
        for (Map<String, Object> item : mapList(question.get("answers"))) {
            explanation = firstText(item.get("explanation"));
            if (StringUtils.hasText(explanation)) {
                return explanation;
            }
        }
        return "";
    }

    private List<String> documentMetadata(Map<String, Object> payload, int questionCount) {
        List<String> metadata = new ArrayList<>();
        metadata.add("题目数：" + questionCount);
        Map<String, Object> generation = mapValue(payload.get("generation"));
        Map<String, Object> blueprint = mapValue(payload.get("blueprint"));
        addMetadata(metadata, "试卷类型", generation.get("paperType"));
        addMetadata(metadata, "总分", firstNonEmpty(blueprint.get("totalScore"), generation.get("totalScore")));
        addMetadata(metadata, "预计用时", firstNonEmpty(blueprint.get("totalEstimatedTime"), generation.get("totalEstimatedTime")), " 分钟");
        return metadata;
    }

    private List<String> questionMetadata(Map<String, Object> question) {
        List<String> metadata = new ArrayList<>();
        Integer type = integerValue(firstNonEmpty(question.get("questionType"), question.get("type")));
        if (type != null) {
            metadata.add("题型：" + questionTypeLabel(type));
        }
        Integer difficulty = integerValue(question.get("difficulty"));
        if (difficulty != null) {
            metadata.add("难度：" + difficultyLabel(difficulty));
        }
        addMetadata(metadata, "分值", question.get("score"));
        addMetadata(metadata, "预计用时", question.get("estimatedTime"), " 分钟");
        return metadata;
    }

    private List<ExportSection> exportSections(Object blueprintValue, List<ExportQuestion> questions) {
        List<Map<String, Object>> rawSections = mapList(mapValue(blueprintValue).get("sections"));
        if (rawSections.isEmpty()) {
            return List.of(new ExportSection("", "", questions));
        }

        List<ExportSection> sections = new ArrayList<>();
        int cursor = 0;
        for (Map<String, Object> rawSection : rawSections) {
            if (cursor >= questions.size()) {
                break;
            }
            Integer targetCount = integerValue(rawSection.get("targetCount"));
            int end = targetCount == null || targetCount <= 0
                    ? questions.size()
                    : Math.min(cursor + targetCount, questions.size());
            if (end <= cursor) {
                continue;
            }
            String sectionTitle = firstText(
                    rawSection.get("sectionTitle"),
                    "第" + firstText(rawSection.get("sectionNo"), String.valueOf(sections.size() + 1)) + "部分");
            sections.add(new ExportSection(
                    sectionTitle,
                    firstText(rawSection.get("scorePerQuestion")),
                    List.copyOf(questions.subList(cursor, end))));
            cursor = end;
        }
        if (cursor < questions.size()) {
            sections.add(new ExportSection("其他题目", "", List.copyOf(questions.subList(cursor, questions.size()))));
        }
        return sections.isEmpty() ? List.of(new ExportSection("", "", questions)) : sections;
    }

    private byte[] renderDocx(ExportDocument document) {
        try (XWPFDocument docx = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph title = docx.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontFamily(DOCX_FONT_FAMILY);
            titleRun.setFontSize(18);
            titleRun.setText(document.title());

            for (String metadata : document.metadata()) {
                addParagraph(docx, metadata, 10, false, ParagraphAlignment.CENTER, 0);
            }

            for (ExportSection section : document.sections()) {
                if (StringUtils.hasText(section.title())) {
                    addParagraph(docx, section.title(), 14, true, ParagraphAlignment.LEFT, 0);
                }
                for (ExportQuestion question : section.questions()) {
                    addQuestionDocx(docx, question, document.includeAnswers());
                }
            }

            docx.write(output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "Failed to export Word document");
        }
    }

    private void addQuestionDocx(XWPFDocument docx, ExportQuestion question, boolean includeAnswers) throws Exception {
        addParagraph(docx, question.number() + ". " + question.title(), 12, true, ParagraphAlignment.LEFT, 0);
        if (!question.metadata().isEmpty()) {
            addParagraph(docx, String.join(" | ", question.metadata()), 10, false, ParagraphAlignment.LEFT, 240);
        }
        if (StringUtils.hasText(question.stem()) && !question.stem().equals(question.title())) {
            addRichParagraphs(docx, question.stem(), 11, false, ParagraphAlignment.LEFT, 240);
        }
        for (ExportOption option : question.options()) {
            String marker = includeAnswers && option.correct() ? " （答案）" : "";
            addRichParagraphs(docx, option.label() + ". " + option.content() + marker, 11, false, ParagraphAlignment.LEFT, 480);
        }
        if (includeAnswers) {
            if (!question.answers().isEmpty()) {
                addRichParagraphs(docx, "答案：" + String.join("；", question.answers()), 11, false, ParagraphAlignment.LEFT, 240);
            }
            if (StringUtils.hasText(question.explanation())) {
                addRichParagraphs(docx, "解析：" + question.explanation(), 11, false, ParagraphAlignment.LEFT, 240);
            }
        }
    }

    private void addParagraph(XWPFDocument docx,
                              String text,
                              int fontSize,
                              boolean bold,
                              ParagraphAlignment alignment,
                              int indentationLeft) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setAlignment(alignment);
        if (indentationLeft > 0) {
            paragraph.setIndentationLeft(indentationLeft);
        }
        XWPFRun run = paragraph.createRun();
        run.setFontFamily(DOCX_FONT_FAMILY);
        run.setFontSize(fontSize);
        run.setBold(bold);
        writeLines(run, text);
    }

    private void addRichParagraphs(XWPFDocument docx,
                                   String text,
                                   int fontSize,
                                   boolean bold,
                                   ParagraphAlignment alignment,
                                   int indentationLeft) throws Exception {
        for (RichBlock block : richText(text).blocks()) {
            XWPFParagraph paragraph = docx.createParagraph();
            paragraph.setAlignment(alignment);
            if (indentationLeft > 0 || block.type() == RichBlockType.ORDERED_ITEM || block.type() == RichBlockType.UNORDERED_ITEM) {
                paragraph.setIndentationLeft(indentationLeft + block.indentationLeft());
            }
            int resolvedFontSize = block.type() == RichBlockType.HEADING ? fontSize + 2 : fontSize;
            boolean resolvedBold = bold || block.type() == RichBlockType.HEADING;
            if (block.type() == RichBlockType.CODE) {
                XWPFRun run = paragraph.createRun();
                run.setFontFamily("Consolas");
                run.setFontSize(fontSize);
                writeLines(run, block.plainText());
                continue;
            }
            appendRichRuns(paragraph, block.inlines(), resolvedFontSize, resolvedBold, block.prefix());
        }
    }

    private void appendRichRuns(XWPFParagraph paragraph,
                                List<RichInline> inlines,
                                int fontSize,
                                boolean bold,
                                String prefix) throws Exception {
        if (StringUtils.hasText(prefix)) {
            XWPFRun prefixRun = paragraph.createRun();
            applyTextStyle(prefixRun, fontSize, bold, false);
            prefixRun.setText(prefix);
        }
        for (RichInline inline : inlines) {
            if (inline instanceof TextInline textInline) {
                XWPFRun run = paragraph.createRun();
                applyTextStyle(run, fontSize, bold || textInline.bold(), textInline.code());
                writeLines(run, textInline.text());
            } else if (inline instanceof FormulaInline formulaInline) {
                addFormulaRun(paragraph, formulaInline, fontSize);
            }
        }
    }

    private void addFormulaRun(XWPFParagraph paragraph, FormulaInline formulaInline, int fontSize) throws Exception {
        LatexImageRenderer.RenderedFormula rendered = latexImageRenderer
                .render(formulaInline.latex(), formulaInline.display())
                .orElse(null);
        XWPFRun run = paragraph.createRun();
        if (rendered == null) {
            applyTextStyle(run, fontSize, false, true);
            run.setText(formulaInline.source());
            return;
        }

        int widthPx = Math.min(rendered.widthPx(), formulaInline.display() ? 520 : 220);
        int heightPx = rendered.widthPx() <= 0
                ? rendered.heightPx()
                : Math.max(1, Math.round(rendered.heightPx() * (widthPx / (float) rendered.widthPx())));
        run.addPicture(
                new ByteArrayInputStream(rendered.png()),
                Document.PICTURE_TYPE_PNG,
                "formula.png",
                Units.toEMU(widthPx),
                Units.toEMU(heightPx));
    }

    private void applyTextStyle(XWPFRun run, int fontSize, boolean bold, boolean code) {
        run.setFontFamily(code ? "Consolas" : DOCX_FONT_FAMILY);
        run.setFontSize(fontSize);
        run.setBold(bold);
    }

    private void writeLines(XWPFRun run, String text) {
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i]);
        }
    }

    private byte[] renderPdf(ExportDocument document) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            exportFontResolver.registerPdfFonts(builder);
            builder.withHtmlContent(renderHtml(document), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to export PDF document", e);
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "Failed to export PDF document");
        }
    }

    String renderHtml(ExportDocument document) {
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8"/>
                  <style>
                    @page {
                      size: A4;
                      margin: 20mm 18mm 18mm;
                      @bottom-center {
                        content: "第 " counter(page) " 页 / 共 " counter(pages) " 页";
                        font-family: 'Noto Sans CJK SC', 'Microsoft YaHei', sans-serif;
                        font-size: 10px;
                        color: #444;
                      }
                    }
                    body {
                      font-family: 'Noto Sans CJK SC', 'Microsoft YaHei', sans-serif;
                      color: #111;
                      font-size: 12px;
                      line-height: 1.58;
                    }
                    h1 { margin: 0 0 8px; text-align: center; font-size: 22px; font-weight: 700; }
                    .paper-meta { margin: 0 0 12px; text-align: center; font-size: 11px; }
                    .candidate-row { width: 100%; margin: 0 0 12px; border-collapse: collapse; table-layout: fixed; }
                    .candidate-row td { padding: 6px 8px; border: 1px solid #111; font-size: 11px; }
                    .score-table { width: 100%; margin: 0 0 12px; border-collapse: collapse; table-layout: fixed; }
                    .score-table th, .score-table td { padding: 5px 6px; border: 1px solid #111; text-align: center; font-size: 10px; }
                    .score-table th { font-weight: 700; background: #f5f5f5; }
                    .notice { margin: 0 0 16px; padding: 8px 10px; border: 1px solid #111; }
                    .notice-title { margin: 0 0 4px; font-weight: 700; }
                    .notice p { margin: 0 0 3px; }
                    h2 { margin: 18px 0 10px; font-size: 15px; font-weight: 700; }
                    .question { margin: 0 0 14px; page-break-inside: avoid; }
                    .question-title { margin: 0 0 6px; font-weight: 700; font-size: 12px; }
                    .question-score { font-weight: 400; }
                    .stem { margin: 0 0 6px; }
                    .options { margin: 4px 0 6px 16px; }
                    .option { margin: 3px 0; }
                    .answer-space { margin: 8px 0 2px; border-bottom: 1px solid #555; height: 28px; }
                    .answer-section { page-break-before: always; }
                    .answer-section h2 { text-align: center; font-size: 18px; }
                    .answer-item { margin: 0 0 12px; page-break-inside: avoid; }
                    .answer-title { margin: 0 0 4px; font-weight: 700; }
                    .answer-block { margin: 4px 0 0 18px; }
                    .rich-text p { margin: 0 0 5px; white-space: pre-wrap; }
                    .rich-text h3 { margin: 8px 0 5px; font-size: 13px; }
                    .rich-text .list-item { margin-left: 14px; }
                    .rich-text pre { margin: 6px 0; padding: 6px 8px; border: 1px solid #999; white-space: pre-wrap; }
                    .rich-text code { font-family: "Consolas", monospace; }
                    .option .rich-text, .option .rich-text p { display: inline; }
                    .formula-inline { max-height: 1.8em; vertical-align: middle; }
                    .formula-display { display: block; max-width: 100%; margin: 8px auto; }
                  </style>
                </head>
                <body>
                """);
        html.append("<h1>").append(escapeHtml(document.title())).append("</h1>");
        if (!document.metadata().isEmpty()) {
            html.append("<p class=\"paper-meta\">").append(escapeHtml(String.join(" | ", document.metadata()))).append("</p>");
        }
        appendCandidateRow(html);
        appendScoreTable(html, document.sections());
        appendNotice(html, document.requirement());
        for (ExportSection section : document.sections()) {
            html.append("<h2>").append(escapeHtml(sectionHeading(section))).append("</h2>");
            for (ExportQuestion question : section.questions()) {
                appendQuestionHtml(html, question);
            }
        }
        if (document.includeAnswers()) {
            appendAnswerSection(html, document.sections());
        }
        html.append("</body></html>");
        return html.toString();
    }

    private void appendCandidateRow(StringBuilder html) {
        html.append("""
                <table class="candidate-row">
                  <tr>
                    <td>姓名：</td>
                    <td>班级：</td>
                    <td>学号：</td>
                    <td>得分：</td>
                  </tr>
                </table>
                """);
    }

    private void appendScoreTable(StringBuilder html, List<ExportSection> sections) {
        html.append("<table class=\"score-table\"><tr><th>大题</th>");
        for (int i = 0; i < sections.size(); i++) {
            html.append("<th>").append(escapeHtml(chineseOrdinal(i + 1))).append("</th>");
        }
        html.append("<th>总分</th></tr><tr><td>题量</td>");
        for (ExportSection section : sections) {
            html.append("<td>").append(section.questions().size()).append("</td>");
        }
        html.append("<td>").append(totalQuestionCount(sections)).append("</td></tr><tr><td>满分</td>");
        for (ExportSection section : sections) {
            html.append("<td>").append(escapeHtml(sectionScore(section))).append("</td>");
        }
        html.append("<td>").append(escapeHtml(totalScore(sections))).append("</td></tr><tr><td>得分</td>");
        for (int i = 0; i < sections.size() + 1; i++) {
            html.append("<td></td>");
        }
        html.append("</tr></table>");
    }

    private void appendNotice(StringBuilder html, String requirement) {
        html.append("<section class=\"notice rich-text\"><p class=\"notice-title\">注意事项</p>");
        String text = StringUtils.hasText(requirement)
                ? requirement
                : "请认真审题，在规定时间内完成作答；选择题请填写选项，非选择题请写明必要过程。";
        appendRichBlocksHtml(html, text);
        html.append("</section>");
    }

    private void appendQuestionHtml(StringBuilder html, ExportQuestion question) {
        html.append("<section class=\"question\">");
        html.append("<p class=\"question-title\">")
                .append(question.number()).append(". ")
                .append("<span class=\"question-score\">")
                .append(escapeHtml(questionScoreLabel(question)))
                .append("</span> ")
                .append(escapeHtml(question.title()))
                .append("</p>");
        if (StringUtils.hasText(question.stem()) && !question.stem().equals(question.title())) {
            appendRichHtml(html, "stem rich-text", question.stem());
        }
        if (!question.options().isEmpty()) {
            html.append("<div class=\"options\">");
            for (ExportOption option : question.options()) {
                html.append("<div class=\"option\"><strong>")
                        .append(escapeHtml(option.label()))
                        .append(".</strong> ")
                        .append("<div class=\"rich-text\">");
                appendRichBlocksHtml(html, option.content());
                html.append("</div></div>");
            }
            html.append("</div>");
        } else if (needsAnswerSpace(question)) {
            html.append("<div class=\"answer-space\"></div><div class=\"answer-space\"></div>");
        }
        html.append("</section>");
    }

    private void appendAnswerSection(StringBuilder html, List<ExportSection> sections) {
        html.append("<section class=\"answer-section\"><h2>参考答案与解析</h2>");
        for (ExportSection section : sections) {
            for (ExportQuestion question : section.questions()) {
                html.append("<div class=\"answer-item\"><p class=\"answer-title\">")
                        .append(question.number()).append(". ")
                        .append(escapeHtml(question.title()))
                        .append(" ")
                        .append(escapeHtml(questionScoreLabel(question)))
                        .append("</p>");
                if (!question.answers().isEmpty()) {
                    appendRichHtml(html, "answer-block rich-text", "答案：" + String.join("；", question.answers()));
                }
                if (StringUtils.hasText(question.explanation())) {
                    appendRichHtml(html, "answer-block rich-text", "解析：" + question.explanation());
                }
                html.append("</div>");
            }
        }
        html.append("</section>");
    }

    private static String sectionHeading(ExportSection section) {
        String title = StringUtils.hasText(section.title()) ? section.title() : "题目";
        return title + "（共 " + section.questions().size() + " 题，共 " + sectionScore(section) + " 分）";
    }

    private static int totalQuestionCount(List<ExportSection> sections) {
        return sections.stream().mapToInt(section -> section.questions().size()).sum();
    }

    private static String sectionScore(ExportSection section) {
        BigDecimal score = section.questions().stream()
                .map(question -> decimalValue(question.score()))
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (BigDecimal.ZERO.compareTo(score) != 0) {
            return displayDecimal(score);
        }

        BigDecimal scorePerQuestion = decimalValue(section.scorePerQuestion());
        if (scorePerQuestion != null) {
            return displayDecimal(scorePerQuestion.multiply(BigDecimal.valueOf(section.questions().size())));
        }
        return "";
    }

    private static String totalScore(List<ExportSection> sections) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasScore = false;
        for (ExportSection section : sections) {
            BigDecimal score = decimalValue(sectionScore(section));
            if (score != null) {
                total = total.add(score);
                hasScore = true;
            }
        }
        return hasScore ? displayDecimal(total) : "";
    }

    private static String questionScoreLabel(ExportQuestion question) {
        return StringUtils.hasText(question.score()) ? "（" + question.score() + " 分）" : "";
    }

    private static boolean needsAnswerSpace(ExportQuestion question) {
        return question.options().isEmpty();
    }

    private static String chineseOrdinal(int value) {
        return switch (value) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            default -> String.valueOf(value);
        };
    }

    private void appendRichHtml(StringBuilder html, String className, String text) {
        html.append("<div class=\"").append(className).append("\">");
        appendRichBlocksHtml(html, text);
        html.append("</div>");
    }

    private void appendRichBlocksHtml(StringBuilder html, String text) {
        for (RichBlock block : richText(text).blocks()) {
            if (block.type() == RichBlockType.CODE) {
                html.append("<pre><code>").append(escapeHtml(block.plainText())).append("</code></pre>");
                continue;
            }

            String tag = block.type() == RichBlockType.HEADING ? "h3" : "p";
            String cssClass = block.type() == RichBlockType.ORDERED_ITEM || block.type() == RichBlockType.UNORDERED_ITEM
                    ? " class=\"list-item\""
                    : "";
            html.append("<").append(tag).append(cssClass).append(">");
            if (StringUtils.hasText(block.prefix())) {
                html.append(escapeHtml(block.prefix()));
            }
            appendRichInlinesHtml(html, block.inlines());
            html.append("</").append(tag).append(">");
        }
    }

    private void appendRichInlinesHtml(StringBuilder html, List<RichInline> inlines) {
        for (RichInline inline : inlines) {
            if (inline instanceof TextInline textInline) {
                appendTextInlineHtml(html, textInline);
            } else if (inline instanceof FormulaInline formulaInline) {
                appendFormulaHtml(html, formulaInline);
            }
        }
    }

    private void appendTextInlineHtml(StringBuilder html, TextInline textInline) {
        String tag = textInline.code() ? "code" : textInline.bold() ? "strong" : "";
        if (StringUtils.hasText(tag)) {
            html.append("<").append(tag).append(">");
        }
        html.append(escapeHtml(textInline.text()));
        if (StringUtils.hasText(tag)) {
            html.append("</").append(tag).append(">");
        }
    }

    private void appendFormulaHtml(StringBuilder html, FormulaInline formulaInline) {
        LatexImageRenderer.RenderedFormula rendered = latexImageRenderer
                .render(formulaInline.latex(), formulaInline.display())
                .orElse(null);
        if (rendered == null) {
            html.append("<code>").append(escapeHtml(formulaInline.source())).append("</code>");
            return;
        }

        html.append("<img alt=\"formula\" class=\"")
                .append(formulaInline.display() ? "formula-display" : "formula-inline")
                .append("\" src=\"data:image/png;base64,")
                .append(Base64.getEncoder().encodeToString(rendered.png()))
                .append("\"/>");
    }

    private static RichText richText(String text) {
        if (!StringUtils.hasText(text)) {
            return new RichText(List.of());
        }

        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<RichBlock> blocks = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            Matcher codeFence = FENCED_CODE_PATTERN.matcher(trimmed);
            if (codeFence.matches()) {
                flushParagraph(blocks, paragraph);
                String fence = codeFence.group(1);
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith(fence)) {
                    if (!code.isEmpty()) {
                        code.append('\n');
                    }
                    code.append(lines[i]);
                    i++;
                }
                blocks.add(new RichBlock(RichBlockType.CODE, List.of(), code.toString(), "", 0));
                continue;
            }

            Matcher bareLatex = BARE_LATEX_BLOCK_PATTERN.matcher(trimmed);
            if (bareLatex.find()) {
                flushParagraph(blocks, paragraph);
                String environment = bareLatex.group(1);
                StringBuilder latex = new StringBuilder(line);
                while (i + 1 < lines.length && !lines[i].contains("\\end{" + environment + "}")) {
                    latex.append('\n').append(lines[++i]);
                }
                blocks.add(new RichBlock(
                        RichBlockType.PARAGRAPH,
                        List.of(new FormulaInline(latex.toString().trim(), true, "$$" + latex.toString().trim() + "$$")),
                        "",
                        "",
                        0));
                continue;
            }

            if (!StringUtils.hasText(trimmed)) {
                flushParagraph(blocks, paragraph);
                continue;
            }

            Matcher heading = HEADING_PATTERN.matcher(trimmed);
            Matcher ordered = ORDERED_ITEM_PATTERN.matcher(trimmed);
            Matcher unordered = UNORDERED_ITEM_PATTERN.matcher(trimmed);
            if (heading.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new RichBlock(RichBlockType.HEADING, parseInlines(heading.group(1)), "", "", 0));
            } else if (ordered.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new RichBlock(RichBlockType.ORDERED_ITEM, parseInlines(ordered.group(2)), "", ordered.group(1) + " ", 240));
            } else if (unordered.matches()) {
                flushParagraph(blocks, paragraph);
                blocks.add(new RichBlock(RichBlockType.UNORDERED_ITEM, parseInlines(unordered.group(1)), "", "• ", 240));
            } else {
                if (!paragraph.isEmpty()) {
                    paragraph.append('\n');
                }
                paragraph.append(line);
            }
        }
        flushParagraph(blocks, paragraph);
        return new RichText(blocks);
    }

    private static void flushParagraph(List<RichBlock> blocks, StringBuilder paragraph) {
        if (paragraph.isEmpty()) {
            return;
        }
        blocks.add(new RichBlock(RichBlockType.PARAGRAPH, parseInlines(paragraph.toString()), "", "", 0));
        paragraph.setLength(0);
    }

    private static List<RichInline> parseInlines(String text) {
        List<RichInline> inlines = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == '`') {
                int end = text.indexOf('`', index + 1);
                if (end > index) {
                    flushTextInlines(inlines, buffer, false);
                    inlines.add(new TextInline(text.substring(index + 1, end), false, true));
                    index = end + 1;
                    continue;
                }
            }
            FormulaMatch formula = formulaAt(text, index);
            if (formula != null) {
                flushTextInlines(inlines, buffer, false);
                inlines.add(new FormulaInline(formula.latex(), formula.display(), formula.source()));
                index = formula.endIndex();
                continue;
            }
            buffer.append(text.charAt(index));
            index++;
        }
        flushTextInlines(inlines, buffer, false);
        return inlines;
    }

    private static FormulaMatch formulaAt(String text, int index) {
        if (text.startsWith("$$", index)) {
            int end = text.indexOf("$$", index + 2);
            if (end > index + 2) {
                String latex = text.substring(index + 2, end);
                return new FormulaMatch(latex, true, "$$" + latex + "$$", end + 2);
            }
        }
        if (text.startsWith("\\[", index)) {
            int end = text.indexOf("\\]", index + 2);
            if (end > index + 2) {
                String latex = text.substring(index + 2, end);
                return new FormulaMatch(latex, true, "\\[" + latex + "\\]", end + 2);
            }
        }
        if (text.startsWith("\\(", index)) {
            int end = text.indexOf("\\)", index + 2);
            if (end > index + 2) {
                String latex = text.substring(index + 2, end);
                return new FormulaMatch(latex, false, "\\(" + latex + "\\)", end + 2);
            }
        }
        if (text.charAt(index) == '$' && !text.startsWith("$$", index)) {
            int end = nextInlineDollar(text, index + 1);
            if (end > index + 1) {
                String latex = text.substring(index + 1, end);
                return new FormulaMatch(latex, false, "$" + latex + "$", end + 1);
            }
        }
        return null;
    }

    private static int nextInlineDollar(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\n') {
                return -1;
            }
            if (current == '$' && (i + 1 >= text.length() || text.charAt(i + 1) != '$')) {
                return i;
            }
        }
        return -1;
    }

    private static void flushTextInlines(List<RichInline> inlines, StringBuilder buffer, boolean bold) {
        if (buffer.isEmpty()) {
            return;
        }
        appendStyledText(inlines, buffer.toString(), bold);
        buffer.setLength(0);
    }

    private static void appendStyledText(List<RichInline> inlines, String text, boolean bold) {
        int start = 0;
        while (start < text.length()) {
            int marker = text.indexOf("**", start);
            if (marker < 0) {
                appendPlainText(inlines, text.substring(start), bold);
                return;
            }
            appendPlainText(inlines, text.substring(start, marker), bold);
            int end = text.indexOf("**", marker + 2);
            if (end < 0) {
                appendPlainText(inlines, text.substring(marker), bold);
                return;
            }
            appendPlainText(inlines, text.substring(marker + 2, end), true);
            start = end + 2;
        }
    }

    private static void appendPlainText(List<RichInline> inlines, String text, boolean bold) {
        String cleaned = text
                .replaceAll("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)", "$1")
                .replaceAll("(?<!_)_([^_\\n]+)_(?!_)", "$1");
        if (!cleaned.isEmpty()) {
            inlines.add(new TextInline(cleaned, bold, false));
        }
    }

    private static void addMetadata(List<String> metadata, String label, Object value) {
        addMetadata(metadata, label, value, "");
    }

    private static void addMetadata(List<String> metadata, String label, Object value, String suffix) {
        String text = displayValue(value);
        if (StringUtils.hasText(text)) {
            metadata.add(label + "：" + text + suffix);
        }
    }

    private static Object firstNonEmpty(Object first, Object second) {
        return StringUtils.hasText(displayValue(first)) ? first : second;
    }

    private static String questionTypeLabel(int type) {
        return switch (type) {
            case 0 -> "单选题";
            case 1 -> "多选题";
            case 2 -> "判断题";
            case 3 -> "填空题";
            case 4 -> "简答题";
            case 5 -> "混合题型";
            default -> "未知";
        };
    }

    private static String difficultyLabel(int difficulty) {
        return switch (difficulty) {
            case 0 -> "随机";
            case 1 -> "简单";
            case 2 -> "中等";
            case 3 -> "困难";
            default -> "未知";
        };
    }

    private static String exportFilename(String title, boolean includeAnswers, String extension) {
        String name = firstText(title, "ai-export")
                .replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_")
                .trim();
        if (name.length() > 80) {
            name = name.substring(0, 80);
        }
        return name + (includeAnswers ? "-教师版" : "-学生版") + extension;
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

    private static BigDecimal decimalValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String displayDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
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

    private static String escapeHtml(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record RichText(List<RichBlock> blocks) {
    }

    private record RichBlock(RichBlockType type,
                             List<RichInline> inlines,
                             String plainText,
                             String prefix,
                             int indentationLeft) {
    }

    private enum RichBlockType {
        PARAGRAPH,
        HEADING,
        CODE,
        ORDERED_ITEM,
        UNORDERED_ITEM
    }

    private interface RichInline {
    }

    private record TextInline(String text, boolean bold, boolean code) implements RichInline {
    }

    private record FormulaInline(String latex, boolean display, String source) implements RichInline {
    }

    private record FormulaMatch(String latex, boolean display, String source, int endIndex) {
    }

    public record ExportFile(String filename, String contentType, byte[] bytes) {
    }

    record ExportDocument(String title,
                          List<String> metadata,
                          String requirement,
                          List<ExportSection> sections,
                          boolean includeAnswers) {
    }

    record ExportSection(String title, String scorePerQuestion, List<ExportQuestion> questions) {
    }

    record ExportQuestion(int number,
                          String title,
                          String stem,
                          String score,
                          List<String> metadata,
                          List<ExportOption> options,
                          List<String> answers,
                          String explanation) {
    }

    record ExportOption(String label, String content, boolean correct, String explanation) {
    }

    private enum ExportFormat {
        PDF("pdf", ".pdf", PDF_CONTENT_TYPE),
        DOCX("docx", ".docx", DOCX_CONTENT_TYPE);

        private final String value;
        private final String extension;
        private final String contentType;

        ExportFormat(String value, String extension, String contentType) {
            this.value = value;
            this.extension = extension;
            this.contentType = contentType;
        }

        private static ExportFormat parse(String value) {
            for (ExportFormat format : values()) {
                if (value != null && format.value.equalsIgnoreCase(value)) {
                    return format;
                }
            }
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Unsupported export format");
        }
    }
}
