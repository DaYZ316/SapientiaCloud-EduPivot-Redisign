package com.dayz.sc.course.model.vo;

import java.util.List;
import java.util.UUID;

/**
 * BatchCreateQuestionsResponse.
 *
 * @author DaYZ
 */
public record BatchCreateQuestionsResponse(
        List<UUID> questionIds,
        int importedCount
) {
}
