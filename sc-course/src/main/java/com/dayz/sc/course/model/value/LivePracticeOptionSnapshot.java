package com.dayz.sc.course.model.value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LivePracticeOptionSnapshot(
        UUID id,
        String optionContent,
        String optionLabel,
        Integer isCorrect,
        BigDecimal score,
        List<String> imageUrls,
        String explanation
) {
}
