package com.dayz.sc.course.model.value;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 随堂练习答案快照值对象
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record LivePracticeAnswerSnapshot(
        UUID id,
        String answerContent,
        String explanation,
        BigDecimal score,
        Integer sortOrder
) {
}
