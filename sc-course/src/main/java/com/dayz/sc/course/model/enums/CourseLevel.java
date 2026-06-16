package com.dayz.sc.course.model.enums;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 级别枚举
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
@RequiredArgsConstructor
public enum CourseLevel {

    BEGINNER(1, "初级"),
    INTERMEDIATE(2, "中级"),
    ADVANCED(3, "高级");

    private final int code;
    private final String description;

    public static CourseLevel fromCode(int code) {
        for (CourseLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        throw new BusinessException(ErrorCodes.BAD_REQUEST);
    }
}
