package com.dayz.sc.ai.model.vo;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;

/**
 * PaperBlueprint.
 *
 * @author DaYZ
 */
public record PaperBlueprint(
        String blueprintId,
        String title,
        String generationStrategy,
        int totalQuestionCount,
        @Nullable BigDecimal totalScore,
        @Nullable Integer totalEstimatedTime,
        List<PaperSectionPlan> sections
) {
}
