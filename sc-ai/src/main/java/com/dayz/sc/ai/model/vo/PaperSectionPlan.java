package com.dayz.sc.ai.model.vo;

import java.math.BigDecimal;
import java.util.List;

public record PaperSectionPlan(
        int sectionNo,
        String sectionTitle,
        Integer questionType,
        Integer difficulty,
        int targetCount,
        BigDecimal scorePerQuestion,
        Integer estimatedTimePerQuestion,
        Integer totalEstimatedTime,
        List<String> knowledgePoints
) {
}
