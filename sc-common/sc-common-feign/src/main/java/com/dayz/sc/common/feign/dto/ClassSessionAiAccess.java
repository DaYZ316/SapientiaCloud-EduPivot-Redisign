package com.dayz.sc.common.feign.dto;

import java.time.Instant;
import java.util.UUID;

public record ClassSessionAiAccess(
        UUID classSessionId,
        UUID courseId,
        UUID teacherId,
        String title,
        Integer classStatus,
        String classStatusText,
        Integer liveStatus,
        String liveStatusText,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        boolean canView,
        boolean canManage
) {
}
