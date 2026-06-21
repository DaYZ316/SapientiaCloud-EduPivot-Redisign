package com.dayz.sc.ai.model.vo;

import java.util.List;
import java.util.Map;

public record GeneratedQuestionDraft(
        String draftId,
        int order,
        Map<String, Object> question,
        List<GenerationValidationIssue> issues
) {
}
