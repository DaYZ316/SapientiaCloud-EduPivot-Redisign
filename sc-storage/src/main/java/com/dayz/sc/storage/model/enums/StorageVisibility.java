package com.dayz.sc.storage.model.enums;

/**
 * StorageVisibility 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public enum StorageVisibility {
    /**
     * 公开可读，任何人可访问
     */
    PUBLIC_READ,
    /**
     * 需要认证，登录用户可访问
     */
    AUTHENTICATED,
    /**
     * 课程私有，仅课程参与者可访问
     */
    COURSE_PRIVATE,
    /**
     * 所有者私有，仅文件所有者可访问
     */
    OWNER_PRIVATE
}
