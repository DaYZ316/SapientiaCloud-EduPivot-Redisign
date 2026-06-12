package com.dayz.sc.notification.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.notification.mapper.NotificationMapper;
import com.dayz.sc.notification.model.entity.Notification;
import com.dayz.sc.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知仓储 MyBatis-Plus 实现。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Repository
@RequiredArgsConstructor
public class MybatisNotificationRepository implements NotificationRepository {

    private final NotificationMapper notificationMapper;

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(notificationMapper.selectById(id));
    }

    @Override
    public void save(Notification notification) {
        notificationMapper.insert(notification);
    }

    @Override
    public void markDeleted(UUID id) {
        notificationMapper.deleteById(id);
    }

    @Override
    public List<Notification> findAll(int page, int size, Integer type, UUID senderId, UUID currentUserId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(Notification::getType, type);
        }
        if (senderId != null) {
            // 我发出的：按发送者过滤
            wrapper.eq(Notification::getSenderId, senderId);
        } else if (currentUserId != null) {
            // 普通查询：全员通知 或 目标包含当前用户且未删除的通知
            // UUID 类型安全，直接拼接不会导致 SQL 注入
            String subSql = "SELECT notification_id FROM ntf_notification_target WHERE user_id = '" + currentUserId + "' AND deleted = 0";
            wrapper.and(w -> w
                    .eq(Notification::getTargetType, 0)
                    .or()
                    .inSql(Notification::getId, subSql)
            );
        }
        wrapper.orderByDesc(Notification::getCreatedAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return notificationMapper.selectList(wrapper);
    }

    @Override
    public long countAll(Integer type, UUID senderId, UUID currentUserId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(Notification::getType, type);
        }
        if (senderId != null) {
            wrapper.eq(Notification::getSenderId, senderId);
        } else if (currentUserId != null) {
            // UUID 类型安全，直接拼接不会导致 SQL 注入
            String subSql = "SELECT notification_id FROM ntf_notification_target WHERE user_id = '" + currentUserId + "' AND deleted = 0";
            wrapper.and(w -> w
                    .eq(Notification::getTargetType, 0)
                    .or()
                    .inSql(Notification::getId, subSql)
            );
        }
        return notificationMapper.selectCount(wrapper);
    }
}
