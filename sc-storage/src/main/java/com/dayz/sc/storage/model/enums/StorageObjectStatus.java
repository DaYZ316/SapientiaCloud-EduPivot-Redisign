package com.dayz.sc.storage.model.enums;

/**
 * 存储对象状态枚举
 *
 * @author DaYZ
 * @since 2026-06-10
 */
public enum StorageObjectStatus {
    /**
     * 待处理状态，文件正在上传或处理中
     */
    PENDING,
    /**
     * 就绪状态，文件已上传完成可用
     */
    READY
}
