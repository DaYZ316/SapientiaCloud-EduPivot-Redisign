package com.dayz.sc.ai.model.vo;

import com.dayz.sc.common.question.CreateQuestionRequest;

import java.util.List;

/**
 * GeneratedQuestionDraft.
 *
 * @author DaYZ
 */
public record GeneratedQuestionDraft(
        String draftId,
        int order,
        CreateQuestionRequest question,
        List<GenerationValidationIssue> issues
) {
}
