package com.dayz.sc.notification.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.notification.mapper.NotificationReadStatusMapper;
import com.dayz.sc.notification.model.entity.NotificationReadStatus;
import com.dayz.sc.notification.repository.NotificationReadStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisNotificationReadStatusRepository implements NotificationReadStatusRepository {

    private final NotificationReadStatusMapper readStatusMapper;

    @Override
    public Optional<NotificationReadStatus> findByNotificationIdAndUserId(UUID notificationId, UUID userId) {
        LambdaQueryWrapper<NotificationReadStatus> wrapper = new LambdaQueryWrapper<NotificationReadStatus>()
                .eq(NotificationReadStatus::getNotificationId, notificationId)
                .eq(NotificationReadStatus::getUserId, userId);
        return Optional.ofNullable(readStatusMapper.selectOne(wrapper));
    }

    @Override
    public void save(NotificationReadStatus readStatus) {
        readStatusMapper.insert(readStatus);
    }

    @Override
    public void saveAll(List<NotificationReadStatus> readStatuses) {
        readStatusMapper.batchInsert(readStatuses);
    }

    @Override
    public List<UUID> findReadNotificationIds(UUID userId, List<UUID> notificationIds) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<NotificationReadStatus> wrapper = new LambdaQueryWrapper<NotificationReadStatus>()
                .eq(NotificationReadStatus::getUserId, userId)
                .in(NotificationReadStatus::getNotificationId, notificationIds);
        return readStatusMapper.selectList(wrapper).stream()
                .map(NotificationReadStatus::getNotificationId)
                .collect(Collectors.toList());
    }

    @Override
    public long countUnread(UUID userId, Integer type) {
        return readStatusMapper.countUnread(userId, type);
    }

    @Override
    public Map<String, Long> countUnreadAll(UUID userId) {
        return readStatusMapper.countUnreadAll(userId);
    }

    @Override
    public void markAllAsRead(UUID userId, Integer type) {
        readStatusMapper.markAllAsRead(userId, type);
    }
}
