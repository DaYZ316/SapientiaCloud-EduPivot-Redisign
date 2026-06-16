package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ClassBarrageMapper;
import com.dayz.sc.course.model.entity.ClassBarrage;
import com.dayz.sc.course.repository.ClassBarrageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * MyBatis repository for class barrage messages.
 */
@Repository
@RequiredArgsConstructor
public class MybatisClassBarrageRepository implements ClassBarrageRepository {

    private final ClassBarrageMapper classBarrageMapper;

    @Override
    public void save(ClassBarrage barrage) {
        classBarrageMapper.insert(barrage);
    }

    @Override
    public Page<ClassBarrage> findBySessionId(UUID sessionId, int page, int size) {
        LambdaQueryWrapper<ClassBarrage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassBarrage::getSessionId, sessionId);
        wrapper.orderByDesc(ClassBarrage::getSentAt);
        return classBarrageMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
