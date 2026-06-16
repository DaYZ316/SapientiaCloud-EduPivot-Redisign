package com.dayz.sc.notification.repository;

import com.dayz.sc.notification.model.entity.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知仓储接口
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public interface NotificationRepository {
    Optional<Notification> findById(UUID id);
    void save(Notification notification);
    void markDeleted(UUID id);
    List<Notification> findAll(int page, int size, Integer type, UUID senderId, UUID currentUserId);
    long countAll(Integer type, UUID senderId, UUID currentUserId);
}
