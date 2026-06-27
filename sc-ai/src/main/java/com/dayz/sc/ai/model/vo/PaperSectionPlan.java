package com.dayz.sc.ai.model.vo;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * PaperSectionPlan.
 *
 * @author DaYZ
 */
public record PaperSectionPlan(
        int sectionNo,
        String sectionTitle,
        @Nullable Integer questionType,
        @Nullable Integer difficulty,
        int targetCount,
        @Nullable BigDecimal scorePerQuestion,
        @Nullable Integer estimatedTimePerQuestion,
        @Nullable Integer totalEstimatedTime,
        List<String> knowledgePoints
) {
}
