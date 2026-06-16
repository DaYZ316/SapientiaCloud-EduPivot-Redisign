package com.dayz.sc.storage.model.enums;

/**
 * 语义化存储桶类型，前端传入此枚举，后端映射真实 MinIO bucket 名称
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public enum StorageBucketType {
    MEDIA,
    COURSE_PUBLIC,
    COURSE_PRIVATE,
    AI
}
