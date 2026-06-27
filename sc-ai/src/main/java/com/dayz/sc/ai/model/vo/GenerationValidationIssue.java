package com.dayz.sc.ai.model.vo;

import org.jspecify.annotations.Nullable;

/**
 * GenerationValidationIssue.
 *
 * @author DaYZ
 */
public record GenerationValidationIssue(
        String code,
        String level,
        String message,
        @Nullable Integer questionIndex,
        @Nullable String repairHint
) {
}
