package com.dayz.sc.course.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Role inside a class session.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Getter
@RequiredArgsConstructor
public enum ClassParticipantRole {

    /**
     * 教师角色，拥有课堂管理权限
     */
    TEACHER(0, "教师"),
    /**
     * 学生角色，参与课堂学习
     */
    STUDENT(1, "学生");

    private final int code;
    private final String description;
}
