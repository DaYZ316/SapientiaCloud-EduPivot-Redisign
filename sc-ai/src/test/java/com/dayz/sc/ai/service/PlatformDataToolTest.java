package com.dayz.sc.ai.service;

import com.dayz.sc.common.feign.client.CourseAiContextClient;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PlatformDataToolTest {

    @Test
    void loadCourseContextShouldCallFeignWhenCourseIdIsNull() {
        CourseAiContextClient client = mock(CourseAiContextClient.class);
        PlatformDataTool tool = new PlatformDataTool(client);
        UUID courseId = UUID.randomUUID();
        AiCourseContext expected = new AiCourseContext(
                List.of(new AiCourseContext.CourseSummary(
                        courseId,
                        "Global AI Course",
                        "Intro",
                        "2026",
                        "Online",
                        1,
                        1,
                        12L,
                        "TEACHER")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(client.getContext(null)).thenReturn(ApiResponse.ok(expected));

        AiCourseContext context = tool.loadCourseContext(null);

        assertThat(context).isSameAs(expected);
        verify(client).getContext(null);
    }

    @Test
    void loadCourseContextShouldReturnClientContextWhenPresent() {
        CourseAiContextClient client = mock(CourseAiContextClient.class);
        PlatformDataTool tool = new PlatformDataTool(client);
        UUID courseId = UUID.randomUUID();
        AiCourseContext expected = new AiCourseContext(
                List.of(new AiCourseContext.CourseSummary(
                        courseId,
                        "AI 课程",
                        "课程介绍",
                        "2026",
                        "线上",
                        1,
                        1,
                        12L,
                        "TEACHER")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        when(client.getContext(courseId)).thenReturn(ApiResponse.ok(expected));

        AiCourseContext context = tool.loadCourseContext(courseId);

        assertThat(context).isSameAs(expected);
        verify(client).getContext(courseId);
    }

    @Test
    void summarizeShouldUseUserFacingChineseLabels() {
        PlatformDataTool tool = new PlatformDataTool(mock(CourseAiContextClient.class));
        UUID courseId = UUID.randomUUID();
        AiCourseContext context = new AiCourseContext(
                List.of(new AiCourseContext.CourseSummary(
                        courseId,
                        "AI 课程",
                        "课程介绍",
                        "2026",
                        "线上",
                        1,
                        1,
                        12L,
                        "TEACHER")),
                List.of(new AiCourseContext.ChapterSummary(
                        UUID.randomUUID(),
                        courseId,
                        "第一章",
                        "认识人工智能",
                        1,
                        1)),
                List.of(new AiCourseContext.QuestionBankSummary(
                        UUID.randomUUID(),
                        courseId,
                        "入门题库",
                        "基础练习",
                        0,
                        2,
                        6L)),
                List.of(new AiCourseContext.QuestionSummary(
                        UUID.randomUUID(),
                        courseId,
                        UUID.randomUUID(),
                        "什么是机器学习？",
                        "请简要说明。",
                        4,
                        2,
                        1)),
                List.of(new AiCourseContext.LivePracticeSummary(
                        UUID.randomUUID(),
                        courseId,
                        UUID.randomUUID(),
                        "课堂小练习",
                        1,
                        5,
                        2)));

        String summary = tool.summarize(context);

        assertThat(summary)
                .contains("课程概况")
                .contains("你的身份：授课教师")
                .contains("当前学生数：12 人")
                .contains("题库情况")
                .contains("入门题库，共 6 道题")
                .contains("课堂小练习，已提交 2 题，共 5 题")
                .doesNotContain("role=", "students=", "submitted=", "questions)", "TEACHER");
    }

    @Test
    void summarizeShouldReturnUserFacingMessageWhenContextMissing() {
        PlatformDataTool tool = new PlatformDataTool(mock(CourseAiContextClient.class));

        assertThat(tool.summarize(null))
                .isEqualTo("暂无可用的课程资料。")
                .doesNotContain("platform context");
    }
}
