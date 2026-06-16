package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 状态枚举
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@RequiredArgsConstructor
public enum EnrollmentStatus {

    /**
     * 待审核状态，等待教师批准
     */
    PENDING(0, "待审核"),
    /**
     * 学习中状态，学生正在学习课程
     */
    ACTIVE(1, "学习中"),
    /**
     * 已结业状态，学生已完成课程
     */
    COMPLETED(2, "已结业"),
    /**
     * 已退课状态，学生已退出课程
     */
    DROPPED(3, "已退课");

    private final int code;
    private final String description;

    public static EnrollmentStatus fromCode(int code) {
        for (EnrollmentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
