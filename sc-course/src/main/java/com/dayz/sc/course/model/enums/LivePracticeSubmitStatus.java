package com.dayz.sc.course.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 随堂练习提交状态枚举
 *
 * @author DaYZ
 * @since 2026-06-18
 */
@Getter
@RequiredArgsConstructor
public enum LivePracticeSubmitStatus {

    /** 未提交 */
    NOT_SUBMITTED(0, "未提交"),

    /** 已提交 */
    SUBMITTED(1, "已提交"),

    /** 补交 */
    LATE_SUBMITTED(2, "补交");

    private final int code;
    private final String description;
}
