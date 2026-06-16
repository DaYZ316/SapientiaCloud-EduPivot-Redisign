package com.dayz.sc.storage.model.enums;

/**
 * StorageUsage 相关定义
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public enum StorageUsage {
    /**
     * 用户头像
     */
    USER_AVATAR,
    /**
     * 课程封面图片
     */
    COURSE_COVER,
    /**
     * 论坛帖子图片
     */
    FORUM_IMAGE,
    /**
     * 课程文件（通用）
     */
    COURSE_FILE,
    /**
     * 课程公开文件，所有参与者可访问
     */
    COURSE_PUBLIC_FILE,
    /**
     * 课程私有文件，仅教师可访问
     */
    COURSE_PRIVATE_FILE,
    /**
     * AI相关文件
     */
    AI_FILE
}
