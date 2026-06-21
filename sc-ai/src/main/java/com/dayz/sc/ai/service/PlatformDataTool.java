package com.dayz.sc.ai.service;

import com.dayz.sc.common.feign.client.CourseAiContextClient;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlatformDataTool {

    private static final AiCourseContext EMPTY_CONTEXT =
            new AiCourseContext(List.of(), List.of(), List.of(), List.of(), List.of());

    private final CourseAiContextClient courseAiContextClient;

    public AiCourseContext loadCourseContext(UUID courseId) {
        ApiResponse<AiCourseContext> response = courseAiContextClient.getContext(courseId);
        if (response == null || response.code() != 0 || response.data() == null) {
            return EMPTY_CONTEXT;
        }
        return response.data();
    }

    public String summarize(AiCourseContext context) {
        if (context == null) {
            return "暂无可用的课程资料。";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("课程概况：\n");
        context.courses().forEach(course -> builder.append("- ")
                .append(course.title())
                .append('\n')
                .append("  你的身份：")
                .append(roleLabel(course.accessRole()))
                .append('\n')
                .append("  当前学生数：")
                .append(numberOrZero(course.activeStudentCount()))
                .append(" 人\n")
                .append("  课程简介：")
                .append(nullToEmpty(course.description()))
                .append('\n'));
        builder.append("\n章节内容：\n");
        context.chapters().stream().limit(30).forEach(chapter -> builder.append("- ")
                .append(chapter.chapterName())
                .append("：")
                .append(nullToEmpty(chapter.description()))
                .append('\n'));
        builder.append("\n题库情况：\n");
        context.questionBanks().forEach(bank -> builder.append("- ")
                .append(bank.bankName())
                .append("，共 ")
                .append(numberOrZero(bank.questionCount()))
                .append(" 道题\n"));
        builder.append("\n近期题目：\n");
        context.questions().stream().limit(30).forEach(question -> builder.append("- ")
                .append(question.questionTitle())
                .append('\n'));
        builder.append("\n课堂练习：\n");
        context.livePractices().stream().limit(20).forEach(practice -> builder.append("- ")
                .append(practice.title())
                .append("，已提交 ")
                .append(numberOrZero(practice.submittedCount()))
                .append(" 题，共 ")
                .append(numberOrZero(practice.totalQuestions()))
                .append(" 题\n"));
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String roleLabel(String role) {
        if ("TEACHER".equalsIgnoreCase(role)) {
            return "授课教师";
        }
        if ("ASSISTANT".equalsIgnoreCase(role)) {
            return "助教";
        }
        if ("STUDENT".equalsIgnoreCase(role)) {
            return "学生";
        }
        return "课程成员";
    }

    private long numberOrZero(Number value) {
        return value == null ? 0 : value.longValue();
    }
}
