package com.dayz.sc.course.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LivePracticeSubmitStatus {

    NOT_SUBMITTED(0, "未提交"),
    SUBMITTED(1, "已提交"),
    LATE_SUBMITTED(2, "补交");

    private final int code;
    private final String description;
}
