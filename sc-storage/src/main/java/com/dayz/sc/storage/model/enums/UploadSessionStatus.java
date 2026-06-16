package com.dayz.sc.storage.model.enums;

/**
 * 状态枚举
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public enum UploadSessionStatus {
    /**
     * 待处理状态，上传会话已创建
     */
    PENDING,
    /**
     * 已完成状态，文件上传成功
     */
    COMPLETED,
    /**
     * 已过期状态，上传会话已超时
     */
    EXPIRED
}
