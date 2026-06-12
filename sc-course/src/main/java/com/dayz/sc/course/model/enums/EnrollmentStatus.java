package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EnrollmentStatus {

    PENDING(0, "待审核"),
    ACTIVE(1, "已选课"),
    COMPLETED(2, "已完成"),
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
