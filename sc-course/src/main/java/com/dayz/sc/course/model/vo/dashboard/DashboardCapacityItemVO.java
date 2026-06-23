package com.dayz.sc.course.model.vo.dashboard;

import java.util.UUID;

public record DashboardCapacityItemVO(
        UUID id,
        String title,
        int value,
        String detail
) {
}
