package com.dayz.sc.common.model;

import lombok.Getter;

/**
 * 用户角色枚举
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Getter
public enum UserRole {

    /**
     * 管理员角色，拥有系统管理权限
     */
    ADMIN(0, "管理员"),
    /**
     * 学生角色，参与课程学习
     */
    STUDENT(1, "学生"),
    /**
     * 教师角色，管理课程内容
     */
    TEACHER(2, "教师");

    private final int code;
    private final String description;

    UserRole(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据整数码获取枚举值
     *
     * @param code 角色码
     * @return 对应的枚举值，未知码返回 null
     */
    public static UserRole fromCode(int code) {
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }

    /**
     * 根据整数码获取枚举值（支持包装类型）
     *
     * @param code 角色码
     * @return 对应的枚举值，null 或未知码返回 null
     */
    public static UserRole fromCode(Integer code) {
        return code != null ? fromCode(code.intValue()) : null;
    }

    /**
     * 判断是否为管理员
     *
     * @return true 如果是管理员
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * 判断是否为教师
     *
     * @return true 如果是教师
     */
    public boolean isTeacher() {
        return this == TEACHER;
    }

    /**
     * 判断是否为学生
     *
     * @return true 如果是学生
     */
    public boolean isStudent() {
        return this == STUDENT;
    }

    /**
     * 判断是否为教师或管理员
     *
     * @return true 如果是教师或管理员
     */
    public boolean isTeacherOrAdmin() {
        return this == TEACHER || this == ADMIN;
    }
}
