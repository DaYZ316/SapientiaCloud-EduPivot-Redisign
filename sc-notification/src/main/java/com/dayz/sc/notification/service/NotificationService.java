package com.dayz.sc.notification.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.notification.model.dto.SendNotificationRequest;
import com.dayz.sc.notification.model.entity.Notification;
import com.dayz.sc.notification.model.entity.NotificationReadStatus;
import com.dayz.sc.notification.model.enums.NotificationType;
import com.dayz.sc.notification.model.enums.TargetType;
import com.dayz.sc.notification.model.vo.NotificationVO;
import com.dayz.sc.notification.model.vo.UnreadCountVO;
import com.dayz.sc.notification.repository.NotificationReadStatusRepository;
import com.dayz.sc.notification.repository.NotificationRepository;
import com.dayz.sc.notification.repository.NotificationTargetRepository;
import com.dayz.sc.notification.sse.NotificationSseEmitter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.dayz.sc.common.util.UuidV7Generator;
import java.util.stream.Collectors;

/**
 * 通知业务服务。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationReadStatusRepository readStatusRepository;
    private final NotificationTargetRepository notificationTargetRepository;
    private final NotificationSseEmitter sseEmitter;

    @Transactional(rollbackFor = Exception.class)
    public UUID sendNotification(SendNotificationRequest request, UUID senderId) {
        if (request.type() == null || request.targetType() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        NotificationType.fromCode(request.type());
        TargetType targetType = TargetType.fromCode(request.targetType());
        if (targetType == TargetType.USER && (request.userIds() == null || request.userIds().isEmpty())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "userIds is required for user-targeted notifications");
        }
        if (targetType == TargetType.CLASS) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Class-targeted notifications are not supported yet");
        }

        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(request.type());
        notification.setTitle(request.title());
        notification.setContent(request.content());
        notification.setSenderId(senderId);
        notification.setTargetType(request.targetType());

        notificationRepository.save(notification);

        // 指定用户时写入目标记录
        if (targetType == TargetType.USER) {
            notificationTargetRepository.saveAllUsers(notification.getId(), request.userIds().stream().distinct().collect(Collectors.toList()));
        }

        pushNotification(targetType, request.userIds(), toNotificationVO(notification, false));
        return notification.getId();
    }

    public PageResponse<NotificationVO> getNotifications(UUID userId, int page, int size, Integer type, boolean sentByMe) {
        UUID senderId = sentByMe ? userId : null;
        UUID currentUserId = sentByMe ? null : userId;
        List<Notification> notifications = notificationRepository.findAll(page, size, type, senderId, currentUserId);
        long total = notificationRepository.countAll(type, senderId, currentUserId);

        List<UUID> notificationIds = notifications.stream()
                .map(Notification::getId)
                .collect(Collectors.toList());
        Set<UUID> readNotificationIds = Set.copyOf(readStatusRepository.findReadNotificationIds(userId, notificationIds));

        List<NotificationVO> vos = notifications.stream()
                .map(notification -> toNotificationVO(notification, readNotificationIds.contains(notification.getId())))
                .collect(Collectors.toList());

        return PageResponse.of(vos, total, page, size);
    }

    public UnreadCountVO getUnreadCount(UUID userId) {
        long totalUnread = readStatusRepository.countUnread(userId, null);
        long systemUnread = readStatusRepository.countUnread(userId, NotificationType.SYSTEM.getCode());
        long teachingUnread = readStatusRepository.countUnread(userId, NotificationType.TEACHING.getCode());
        return new UnreadCountVO(totalUnread, systemUnread, teachingUnread);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(UUID notificationId, UUID userId) {
        notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOTIFICATION_NOT_FOUND));

        readStatusRepository.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresentOrElse(
                        readStatus -> {},
                        () -> {
                            NotificationReadStatus readStatus = new NotificationReadStatus();
                            readStatus.setId(UuidV7Generator.generate());
                            readStatus.setNotificationId(notificationId);
                            readStatus.setUserId(userId);
                            readStatus.setReadAt(Instant.now());
                            readStatusRepository.save(readStatus);
                        }
                );
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(UUID userId, Integer type) {
        readStatusRepository.markAllAsRead(userId, type);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOTIFICATION_NOT_FOUND));
        if (userId.equals(notification.getSenderId())) {
            throw new BusinessException(ErrorCodes.NOTIFICATION_RECALL_FORBIDDEN);
        }
        notificationTargetRepository.markDeleted(notificationId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllNotifications(UUID userId, Integer type) {
        notificationTargetRepository.markAllDeleted(userId, type);
    }

    /**
     * 撤回通知（发送者或管理员可操作，对所有接收者生效）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recallNotification(UUID notificationId, UUID senderId, Integer role) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOTIFICATION_NOT_FOUND));
        // 非管理员只能撤回自己发出的通知
        if (role == null || role != 0) {
            if (!senderId.equals(notification.getSenderId())) {
                throw new BusinessException(ErrorCodes.NOTIFICATION_RECALL_FORBIDDEN);
            }
        }
        notificationRepository.markDeleted(notificationId);
    }

    private void pushNotification(TargetType targetType, List<UUID> userIds, NotificationVO notification) {
        if (targetType == TargetType.USER) {
            userIds.stream()
                    .distinct()
                    .forEach(userId -> sseEmitter.sendToUser(userId, notification));
            return;
        }

        sseEmitter.broadcast(notification);
    }

    private NotificationVO toNotificationVO(Notification notification, boolean isRead) {
        return new NotificationVO(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getSenderId(),
                notification.getTargetType(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                isRead,
                null
        );
    }
}
