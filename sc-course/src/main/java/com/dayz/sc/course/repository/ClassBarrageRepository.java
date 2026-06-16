package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ClassBarrage;

import java.util.UUID;

/**
 * Repository for class barrage messages.
 */
public interface ClassBarrageRepository {

    void save(ClassBarrage barrage);

    Page<ClassBarrage> findBySessionId(UUID sessionId, int page, int size);
}
