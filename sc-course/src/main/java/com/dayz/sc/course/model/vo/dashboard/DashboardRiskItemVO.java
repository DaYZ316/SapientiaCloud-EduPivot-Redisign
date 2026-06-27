package com.dayz.sc.course.model.vo.dashboard;

/**
 * DashboardRiskItemVO.
 *
 * @author DaYZ
 */
public record DashboardRiskItemVO(
        String title,
        String detail,
        String status,
        String level,
        String icon
) {
}
