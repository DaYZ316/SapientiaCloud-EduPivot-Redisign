package com.dayz.sc.course.model.dto;

import java.util.List;
import java.util.UUID;

/**
 * 提交随堂练习答案请求
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public record SubmitLivePracticeAnswerRequest(
        List<UUID> selectedOptionIds,
        String textAnswer
) {
}
