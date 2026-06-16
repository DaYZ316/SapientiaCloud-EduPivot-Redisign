package com.dayz.sc.notification.repository;

import java.util.List;
import java.util.UUID;

/**
 * 通知目标用户仓储接口
 *
 * @author DaYZ
 * @since 2026-06-11
 */
public interface NotificationTargetRepository {
    void saveAll(List<UUID> notificationIds, UUID userId);
    void saveAllUsers(UUID notificationId, List<UUID> userIds);
    List<UUID> findNotificationIdsByUserId(UUID userId);
    void markDeleted(UUID notificationId, UUID userId);
    void markAllDeleted(UUID userId, Integer type);
}
