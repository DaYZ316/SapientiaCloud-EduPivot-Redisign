package com.dayz.sc.notification.repository;

import com.dayz.sc.notification.model.entity.NotificationReadStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知已读状态仓储接口。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public interface NotificationReadStatusRepository {
    Optional<NotificationReadStatus> findByNotificationIdAndUserId(UUID notificationId, UUID userId);
    void save(NotificationReadStatus readStatus);
    void saveAll(List<NotificationReadStatus> readStatuses);
    List<UUID> findReadNotificationIds(UUID userId, List<UUID> notificationIds);
    long countUnread(UUID userId, Integer type);
    void markAllAsRead(UUID userId, Integer type);
}
