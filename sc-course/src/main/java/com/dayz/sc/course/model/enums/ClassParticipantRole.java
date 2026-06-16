package com.dayz.sc.course.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Role inside a class session.
 */
@Getter
@RequiredArgsConstructor
public enum ClassParticipantRole {

    TEACHER(0, "教师"),
    STUDENT(1, "学生");

    private final int code;
    private final String description;
}
