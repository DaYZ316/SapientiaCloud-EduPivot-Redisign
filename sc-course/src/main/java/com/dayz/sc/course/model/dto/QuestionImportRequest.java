package com.dayz.sc.course.model.dto;

import com.dayz.sc.common.question.QuestionAnswerRequest;
import com.dayz.sc.common.question.QuestionOptionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record QuestionImportRequest(
        @NotBlank @Size(max = 500) String questionTitle,
        String questionContent,
        int questionType,
        int difficulty,
        @Min(0) BigDecimal score,
        Integer estimatedTime,
        List<String> tags,
        List<String> imageUrls,
        int allowPartialCredit,
        List<@Valid QuestionOptionRequest> options,
        List<@Valid QuestionAnswerRequest> answers
) {
}
