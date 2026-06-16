package com.dayz.sc.notification.repository;

import com.dayz.sc.notification.model.entity.NotificationReadStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知已读状态仓储接口
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public interface NotificationReadStatusRepository {

    /**
     * 根据通知ID和用户ID查询已读状态。
     *
     * @param notificationId 通知ID
     * @param userId         用户ID
     * @return 已读状态实体，可能为空
     */
    Optional<NotificationReadStatus> findByNotificationIdAndUserId(UUID notificationId, UUID userId);

    /**
     * 保存已读状态。
     *
     * @param readStatus 已读状态实体
     */
    void save(NotificationReadStatus readStatus);

    /**
     * 批量保存已读状态。
     *
     * @param readStatuses 已读状态列表
     */
    void saveAll(List<NotificationReadStatus> readStatuses);

    /**
     * 查询用户已读的通知ID列表。
     *
     * @param userId          用户ID
     * @param notificationIds 通知ID列表
     * @return 已读的通知ID列表
     */
    List<UUID> findReadNotificationIds(UUID userId, List<UUID> notificationIds);

    /**
     * 统计用户的未读通知数量。
     *
     * @param userId 用户ID
     * @param type   通知类型
     * @return 未读通知数量
     */
    long countUnread(UUID userId, Integer type);

    /**
     * 统计用户所有类型的未读通知数量。
     *
     * @param userId 用户ID
     * @return 通知类型与未读数量的映射
     */
    Map<String, Long> countUnreadAll(UUID userId);

    /**
     * 将用户的所有通知标记为已读。
     *
     * @param userId 用户ID
     * @param type   通知类型
     */
    void markAllAsRead(UUID userId, Integer type);
}
