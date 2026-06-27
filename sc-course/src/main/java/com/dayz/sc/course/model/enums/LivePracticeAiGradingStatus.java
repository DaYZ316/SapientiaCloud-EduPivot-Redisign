package com.dayz.sc.course.model.enums;

/**
 * 课堂练习 AI 批改状态
 *
 * @author DaYZ
 * @since 2026-06-27
 */
public enum LivePracticeAiGradingStatus {

    /**
     * 无需批改
     */
    NOT_REQUIRED,

    /**
     * 待批改
     */
    PENDING,

    /**
     * 批改完成
     */
    COMPLETED,

    /**
     * 批改失败
     */
    FAILED
}
