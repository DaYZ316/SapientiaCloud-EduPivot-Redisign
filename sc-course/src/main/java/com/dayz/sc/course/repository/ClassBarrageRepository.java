package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ClassBarrage;

import java.util.UUID;

/**
 * Repository for class barrage messages.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public interface ClassBarrageRepository {

    /**
     * 保存弹幕消息
     *
     * @param barrage 弹幕实体
     */
    void save(ClassBarrage barrage);

    /**
     * 根据课堂会话ID分页查询弹幕
     *
     * @param sessionId 课堂会话ID
     * @param page      页码
     * @param size      每页大小
     * @return 分页结果
     */
    Page<ClassBarrage> findBySessionId(UUID sessionId, int page, int size);
}
