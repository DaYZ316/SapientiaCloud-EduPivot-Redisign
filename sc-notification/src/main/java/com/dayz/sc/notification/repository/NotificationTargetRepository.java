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

    /**
     * 批量保存通知目标用户（同一用户多条通知）
     *
     * @param notificationIds 通知ID列表
     * @param userId          用户ID
     */
    void saveAll(List<UUID> notificationIds, UUID userId);

    /**
     * 批量保存通知目标用户（同一通知多条用户）
     *
     * @param notificationId 通知ID
     * @param userIds        用户ID列表
     */
    void saveAllUsers(UUID notificationId, List<UUID> userIds);

    /**
     * 查询用户的所有通知ID
     *
     * @param userId 用户ID
     * @return 通知ID列表
     */
    List<UUID> findNotificationIdsByUserId(UUID userId);

    /**
     * 标记通知目标为已删除
     *
     * @param notificationId 通知ID
     * @param userId         用户ID
     */
    void markDeleted(UUID notificationId, UUID userId);

    /**
     * 标记用户的所有通知目标为已删除
     *
     * @param userId 用户ID
     * @param type   通知类型
     */
    void markAllDeleted(UUID userId, Integer type);
}
