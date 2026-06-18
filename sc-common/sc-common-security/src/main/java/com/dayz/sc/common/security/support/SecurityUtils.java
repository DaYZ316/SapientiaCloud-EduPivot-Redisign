package com.dayz.sc.common.security.support;

import com.dayz.sc.common.model.UserRole;

/**
 * 安全相关工具类
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public final class SecurityUtils {

    /**
     * 管理员角色代码
     */
    public static final int ROLE_ADMIN = UserRole.ADMIN.getCode();

    /**
     * 学生角色代码
     */
    public static final int ROLE_STUDENT = UserRole.STUDENT.getCode();

    /**
     * 教师角色代码
     */
    public static final int ROLE_TEACHER = UserRole.TEACHER.getCode();

    private SecurityUtils() {
    }

    /**
     * 判断当前用户是否为管理员
     *
     * @param role 角色代码
     * @return true 如果是管理员
     */
    public static boolean isAdmin(Integer role) {
        return UserRole.fromCode(role) == UserRole.ADMIN;
    }

    /**
     * 判断当前用户是否为教师
     *
     * @param role 角色代码
     * @return true 如果是教师
     */
    public static boolean isTeacher(Integer role) {
        return UserRole.fromCode(role) == UserRole.TEACHER;
    }

    /**
     * 判断当前用户是否为学生
     *
     * @param role 角色代码
     * @return true 如果是学生
     */
    public static boolean isStudent(Integer role) {
        return UserRole.fromCode(role) == UserRole.STUDENT;
    }

    /**
     * 判断当前用户是否为教师或管理员
     *
     * @param role 角色代码
     * @return true 如果是教师或管理员
     */
    public static boolean isTeacherOrAdmin(Integer role) {
        UserRole r = UserRole.fromCode(role);
        return r != null && r.isTeacherOrAdmin();
    }
}
