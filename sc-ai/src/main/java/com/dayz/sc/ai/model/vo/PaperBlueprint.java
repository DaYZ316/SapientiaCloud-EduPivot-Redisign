package com.dayz.sc.ai.model.vo;

import java.math.BigDecimal;
import java.util.List;

public record PaperBlueprint(
        String blueprintId,
        String title,
        String generationStrategy,
        int totalQuestionCount,
        BigDecimal totalScore,
        Integer totalEstimatedTime,
        List<PaperSectionPlan> sections
) {
}
