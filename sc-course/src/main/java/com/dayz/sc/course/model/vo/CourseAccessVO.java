package com.dayz.sc.course.model.vo;

import java.util.UUID;

public record CourseAccessVO(
        UUID courseId,
        boolean canManage,
        boolean canReadPublic,
        boolean canReadPrivate
) {}
