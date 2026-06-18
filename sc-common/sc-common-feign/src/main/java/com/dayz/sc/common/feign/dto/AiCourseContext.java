package com.dayz.sc.common.feign.dto;

import java.util.List;
import java.util.UUID;

public record AiCourseContext(
        List<CourseSummary> courses,
        List<ChapterSummary> chapters,
        List<QuestionBankSummary> questionBanks,
        List<QuestionSummary> questions,
        List<LivePracticeSummary> livePractices
) {
    public record CourseSummary(
            UUID id,
            String title,
            String description,
            String semester,
            String location,
            Integer level,
            Integer status,
            Long activeStudentCount,
            String accessRole
    ) {
    }

    public record ChapterSummary(
            UUID id,
            UUID courseId,
            String chapterName,
            String description,
            Integer sortOrder,
            Integer status
    ) {
    }

    public record QuestionBankSummary(
            UUID id,
            UUID courseId,
            String bankName,
            String description,
            Integer bankType,
            Integer difficulty,
            Long questionCount
    ) {
    }

    public record QuestionSummary(
            UUID id,
            UUID courseId,
            UUID questionBankId,
            String questionTitle,
            String questionContent,
            Integer questionType,
            Integer difficulty,
            Integer status
    ) {
    }

    public record LivePracticeSummary(
            UUID id,
            UUID courseId,
            UUID classSessionId,
            String title,
            Integer publishOrder,
            Integer totalQuestions,
            Integer submittedCount
    ) {
    }
}
