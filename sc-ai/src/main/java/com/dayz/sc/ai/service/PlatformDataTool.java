package com.dayz.sc.ai.service;

import com.dayz.sc.common.feign.client.CourseAiContextClient;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlatformDataTool {

    private final CourseAiContextClient courseAiContextClient;

    public AiCourseContext loadCourseContext(UUID courseId) {
        ApiResponse<AiCourseContext> response = courseAiContextClient.getContext(courseId);
        if (response == null || response.code() != 0 || response.data() == null) {
            return new AiCourseContext(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        }
        return response.data();
    }

    public String summarize(AiCourseContext context) {
        if (context == null) {
            return "No platform context available.";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Courses:\n");
        context.courses().forEach(course -> builder.append("- ")
                .append(course.title())
                .append(" (role=")
                .append(course.accessRole())
                .append(", students=")
                .append(course.activeStudentCount())
                .append(")\n")
                .append("  ")
                .append(nullToEmpty(course.description()))
                .append('\n'));
        builder.append("\nChapters:\n");
        context.chapters().stream().limit(30).forEach(chapter -> builder.append("- ")
                .append(chapter.chapterName())
                .append(": ")
                .append(nullToEmpty(chapter.description()))
                .append('\n'));
        builder.append("\nQuestion banks:\n");
        context.questionBanks().forEach(bank -> builder.append("- ")
                .append(bank.bankName())
                .append(" (")
                .append(bank.questionCount())
                .append(" questions)\n"));
        builder.append("\nRecent questions:\n");
        context.questions().stream().limit(30).forEach(question -> builder.append("- ")
                .append(question.questionTitle())
                .append('\n'));
        builder.append("\nLive practices:\n");
        context.livePractices().stream().limit(20).forEach(practice -> builder.append("- ")
                .append(practice.title())
                .append(" (submitted=")
                .append(practice.submittedCount())
                .append("/")
                .append(practice.totalQuestions())
                .append(")\n"));
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
