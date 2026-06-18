package com.dayz.sc.ai.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GenerationRequest(
        UUID questionBankId,
        @Min(1) @Max(50) Integer questionCount,
        Integer questionType,
        Integer difficulty,
        BigDecimal scorePerQuestion,
        BigDecimal totalScore,
        Integer totalEstimatedTime,
        @Size(max = 200) String paperName,
        @Size(max = 100) String paperType,
        @Size(max = 1000) String requirement,
        List<UUID> chapterIds,
        List<String> knowledgePoints,
        List<String> abilityGoals
) {
}
