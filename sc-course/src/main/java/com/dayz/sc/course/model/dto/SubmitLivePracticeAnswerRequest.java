package com.dayz.sc.course.model.dto;

import java.util.List;
import java.util.UUID;

public record SubmitLivePracticeAnswerRequest(
        List<UUID> selectedOptionIds,
        String textAnswer
) {
}
