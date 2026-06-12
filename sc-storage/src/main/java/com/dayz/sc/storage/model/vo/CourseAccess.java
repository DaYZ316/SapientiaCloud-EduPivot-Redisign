package com.dayz.sc.storage.model.vo;

import java.util.UUID;

public record CourseAccess(
        UUID courseId,
        boolean canManage,
        boolean canReadPublic,
        boolean canReadPrivate,
        boolean isPrimaryTeacher
) {}
