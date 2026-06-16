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

    /**
     * 根据ID查询通知。
     *
     * @param id 通知ID
     * @return 通知实体，可能为空
     */
    Optional<Notification> findById(UUID id);

    /**
     * 保存通知。
     *
     * @param notification 通知实体
     */
    void save(Notification notification);

    /**
     * 标记通知为已删除。
     *
     * @param id 通知ID
     */
    void markDeleted(UUID id);

    /**
     * 分页查询通知。
     *
     * @param page          页码
     * @param size          每页大小
     * @param type          通知类型
     * @param senderId      发送者ID
     * @param currentUserId 当前用户ID
     * @return 通知列表
     */
    List<Notification> findAll(int page, int size, Integer type, UUID senderId, UUID currentUserId);

    /**
     * 统计通知数量。
     *
     * @param type          通知类型
     * @param senderId      发送者ID
     * @param currentUserId 当前用户ID
     * @return 通知数量
     */
    long countAll(Integer type, UUID senderId, UUID currentUserId);
}
