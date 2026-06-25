package com.dayz.sc.course.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 随堂练习题目统计分析视图对象
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record LivePracticeAnalysisVO(
        @JsonProperty Integer submittedCount,
        @JsonProperty Integer lateSubmittedCount,
        @JsonProperty Integer notSubmittedCount,
        @JsonProperty Integer correctCount,
        @JsonProperty BigDecimal averageScore,
        @JsonProperty Map<String, Integer> optionCounts,
        @JsonProperty List<LivePracticeStudentVO> notSubmittedStudents,
        @JsonProperty List<LivePracticeSubmissionVO> submissions
) {
}
