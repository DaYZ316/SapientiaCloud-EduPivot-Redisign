package com.dayz.sc.course.model.vo;

import java.util.List;
import java.util.UUID;

public record BatchCreateQuestionsResponse(
        List<UUID> questionIds,
        int importedCount
) {
}
