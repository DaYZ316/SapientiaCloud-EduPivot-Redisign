package com.dayz.sc.ai.model.vo;

public record GenerationValidationIssue(
        String code,
        String level,
        String message,
        Integer questionIndex,
        String repairHint
) {
}
