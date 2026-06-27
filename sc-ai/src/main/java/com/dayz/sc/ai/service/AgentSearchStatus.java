package com.dayz.sc.ai.service;

/**
 * Agent 搜索结果状态
 *
 * @author DaYZ
 * @since 2026-06-27
 */
public enum AgentSearchStatus {

    /**
     * 搜索成功
     */
    OK,

    /**
     * 搜索结果为空
     */
    EMPTY,

    /**
     * 搜索功能已禁用
     */
    DISABLED,

    /**
     * 搜索配置错误
     */
    MISCONFIGURED,

    /**
     * 搜索失败
     */
    FAILED
}
