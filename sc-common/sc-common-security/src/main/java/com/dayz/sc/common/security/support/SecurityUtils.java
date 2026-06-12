package com.dayz.sc.common.security.support;

/**
 * 安全相关工具类。
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public final class SecurityUtils {

    /**
     * 管理员角色代码。
     */
    public static final int ROLE_ADMIN = 0;

    /**
     * 教师角色代码。
     */
    public static final int ROLE_TEACHER = 2;

    private SecurityUtils() {
    }

    /**
     * 判断当前用户是否为管理员。
     *
     * @param role 角色代码
     * @return true 如果是管理员
     */
    public static boolean isAdmin(Integer role) {
        return role != null && role == ROLE_ADMIN;
    }

    /**
     * 判断当前用户是否为教师或管理员。
     *
     * @param role 角色代码
     * @return true 如果是教师或管理员
     */
    public static boolean isTeacherOrAdmin(Integer role) {
        return role != null && (role == ROLE_ADMIN || role == ROLE_TEACHER);
    }
}
