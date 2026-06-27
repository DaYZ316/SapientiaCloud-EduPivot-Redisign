package com.dayz.sc.ai.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * GenerationRequest.
 *
 * @author DaYZ
 */
public record GenerationRequest(
        @Nullable UUID questionBankId,
        @Nullable @Min(1) @Max(50) Integer questionCount,
        @Nullable Integer questionType,
        @Nullable Integer difficulty,
        @Nullable BigDecimal scorePerQuestion,
        @Nullable BigDecimal totalScore,
        @Nullable Integer totalEstimatedTime,
        @Nullable @Size(max = 200) String paperName,
        @Nullable @Size(max = 100) String paperType,
        @Nullable @Size(max = 1000) String requirement,
        @Nullable List<UUID> chapterIds,
        @Nullable List<String> knowledgePoints,
        @Nullable List<String> abilityGoals
) {
}
