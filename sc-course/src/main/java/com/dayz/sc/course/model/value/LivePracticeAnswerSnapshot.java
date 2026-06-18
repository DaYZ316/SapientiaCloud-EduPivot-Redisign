package com.dayz.sc.course.model.value;

import java.math.BigDecimal;
import java.util.UUID;

public record LivePracticeAnswerSnapshot(
        UUID id,
        String answerContent,
        String explanation,
        BigDecimal score,
        Integer sortOrder
) {
}
