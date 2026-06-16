package com.dayz.sc.notification.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.notification.mapper.NotificationTargetMapper;
import com.dayz.sc.notification.model.entity.NotificationTarget;
import com.dayz.sc.notification.repository.NotificationTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 通知目标用户仓储 MyBatis-Plus 实现
 *
 * @author DaYZ
 * @since 2026-06-11
 */
@Repository
@RequiredArgsConstructor
public class MybatisNotificationTargetRepository implements NotificationTargetRepository {

    private final NotificationTargetMapper targetMapper;

    @Override
    public void saveAll(List<UUID> notificationIds, UUID userId) {
        List<NotificationTarget> targets = notificationIds.stream().map(notificationId -> {
            NotificationTarget target = new NotificationTarget();
            target.setId(UuidV7Generator.generate());
            target.setNotificationId(notificationId);
            target.setUserId(userId);
            return target;
        }).toList();
        targetMapper.batchInsert(targets);
    }

    @Override
    public void saveAllUsers(UUID notificationId, List<UUID> userIds) {
        List<NotificationTarget> targets = userIds.stream().map(userId -> {
            NotificationTarget target = new NotificationTarget();
            target.setId(UuidV7Generator.generate());
            target.setNotificationId(notificationId);
            target.setUserId(userId);
            return target;
        }).toList();
        targetMapper.batchInsert(targets);
    }

    @Override
    public List<UUID> findNotificationIdsByUserId(UUID userId) {
        LambdaQueryWrapper<NotificationTarget> wrapper = new LambdaQueryWrapper<NotificationTarget>()
                .eq(NotificationTarget::getUserId, userId);
        return targetMapper.selectList(wrapper).stream()
                .map(NotificationTarget::getNotificationId)
                .collect(Collectors.toList());
    }

    @Override
    public void markDeleted(UUID notificationId, UUID userId) {
        LambdaUpdateWrapper<NotificationTarget> wrapper = new LambdaUpdateWrapper<NotificationTarget>()
                .eq(NotificationTarget::getNotificationId, notificationId)
                .eq(NotificationTarget::getUserId, userId)
                .set(NotificationTarget::getDeleted, 1);
        targetMapper.update(null, wrapper);
    }

    @Override
    public void markAllDeleted(UUID userId, Integer type) {
        targetMapper.markAllDeleted(userId, type);
    }
}
