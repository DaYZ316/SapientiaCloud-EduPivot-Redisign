package com.dayz.sc.ai.service;

/**
 * Agent 搜索事件发射器
 *
 * @author DaYZ
 * @since 2026-06-27
 */
@FunctionalInterface
public interface AgentSearchEventEmitter {

    /**
     * 发射搜索事件
     *
     * @param event 搜索事件
     */
    void emit(AgentSearchEvent event);
}
