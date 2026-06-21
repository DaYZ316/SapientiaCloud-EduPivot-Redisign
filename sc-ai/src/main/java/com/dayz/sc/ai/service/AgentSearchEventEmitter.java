package com.dayz.sc.ai.service;

@FunctionalInterface
public interface AgentSearchEventEmitter {
    void emit(AgentSearchEvent event);
}
