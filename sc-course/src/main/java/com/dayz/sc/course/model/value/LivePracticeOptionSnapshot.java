package com.dayz.sc.course.model.value;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 随堂练习选项快照值对象
 *
 * @author DaYZ
 * @since 2026-06-18
 */
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
