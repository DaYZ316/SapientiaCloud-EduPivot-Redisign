package com.dayz.aeroverse.auth.model.enums;

/**
 * 系统账号生命周期状态。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public enum UserStatus {
    /**
     * 账号可正常登录和使用。
     */
    ACTIVE,
    /**
     * 账号被停用，暂不可登录。
     */
    DISABLED,
    /**
     * 账号已删除，仅保留必要审计数据。
     */
    DELETED
}
