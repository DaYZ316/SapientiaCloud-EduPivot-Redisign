package com.dayz.sc.storage.model.enums;

/**
 * 语义化存储桶类型，前端传入此枚举，后端映射真实 MinIO bucket 名称
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public enum StorageBucketType {
    /**
     * 媒体文件存储桶，用于用户头像等
     */
    MEDIA,
    /**
     * 课程公开文件存储桶，所有课程参与者可访问
     */
    COURSE_PUBLIC,
    /**
     * 课程私有文件存储桶，仅教师可访问
     */
    COURSE_PRIVATE,
    /**
     * AI文件存储桶，用于AI相关文件
     */
    AI
}
