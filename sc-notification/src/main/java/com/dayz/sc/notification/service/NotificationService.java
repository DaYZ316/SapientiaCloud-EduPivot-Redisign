package com.dayz.sc.notification.service;

import com.dayz.sc.common.dashboard.DashboardChartPoint;
import com.dayz.sc.common.dashboard.DashboardNotificationItem;
import com.dayz.sc.common.dashboard.DashboardNotificationSummary;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.UuidV7Generator;
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
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 閫氱煡涓氬姟鏈嶅姟
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
    private final UnreadCountService unreadCountService;

    @Transactional(rollbackFor = Exception.class)
    public UUID sendNotification(SendNotificationRequest request, UUID senderId) {
        if (request.type() == null || request.targetType() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        NotificationType.fromCode(request.type());
        TargetType targetType = TargetType.fromCode(request.targetType());
        if (targetType == TargetType.USER) {
            boolean hasUserIds = request.userIds() != null && !request.userIds().isEmpty();
            if (!hasUserIds) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "userIds is required for user-targeted notifications");
            }
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
        Instant now = Instant.now();
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);

        notificationRepository.save(notification);

        // 鎸囧畾鐢ㄦ埛鏃跺啓鍏ョ洰鏍囪褰曪紙鎺掗櫎鍙戦€佽€呰嚜宸憋級
        if (targetType == TargetType.USER) {
            List<UUID> targetUserIds = request.userIds().stream()
                    .distinct()
                    .filter(id -> !id.equals(senderId))
                    .collect(Collectors.toList());
            if (!targetUserIds.isEmpty()) {
                notificationTargetRepository.saveAllUsers(notification.getId(), targetUserIds);
            }
            // 鏇存柊 Redis 鏈璁℃暟骞舵帹閫侊紙鎼哄甫绮剧‘ count锛?
            for (UUID uid : targetUserIds) {
                UnreadCountVO countVO = unreadCountService.increment(uid, request.type());
                long count = countVO != null ? countVO.total() : -1;
                sseEmitter.sendToUser(uid, toNotificationVO(notification, false), count);
            }
        } else {
            // 骞挎挱锛氭棤娉曠簿纭?increment 鎵€鏈夌敤鎴凤紝SSE 鎼哄甫 count=-1 璁╁墠绔厹搴曟煡璇?
            pushNotification(targetType, senderId, request.userIds(), toNotificationVO(notification, false));
        }
        return notification.getId();
    }

    public PageResponse<@NonNull NotificationVO> getNotifications(UUID userId, int page, int size, Integer type, boolean sentByMe) {
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
        return unreadCountService.getOrInitFromDb(userId, readStatusRepository);
    }

    public DashboardNotificationSummary getDashboardSummary(UUID userId, boolean includeDistribution) {
        UnreadCountVO unreadCount = getUnreadCount(userId);
        PageResponse<@NonNull NotificationVO> recent = getNotifications(userId, 1, 5, null, false);
        List<DashboardNotificationItem> recentItems = recent.records().stream()
                .map(item -> new DashboardNotificationItem(
                        item.id(),
                        item.type(),
                        item.title(),
                        item.createdAt(),
                        Boolean.TRUE.equals(item.read())))
                .toList();
        List<DashboardChartPoint> distribution = includeDistribution
                ? List.of(
                new DashboardChartPoint("绯荤粺鏈", unreadCount.system()),
                new DashboardChartPoint("鏁欏鏈", unreadCount.teaching()),
                new DashboardChartPoint("杩戞湡宸茶", recentItems.stream().filter(DashboardNotificationItem::read).count()))
                : List.of();
        return new DashboardNotificationSummary(
                unreadCount.total(),
                unreadCount.system(),
                unreadCount.teaching(),
                recentItems,
                distribution);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOTIFICATION_NOT_FOUND));

        readStatusRepository.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresentOrElse(
                        readStatus -> {
                        },
                        () -> {
                            NotificationReadStatus readStatus = new NotificationReadStatus();
                            readStatus.setId(UuidV7Generator.generate());
                            readStatus.setNotificationId(notificationId);
                            readStatus.setUserId(userId);
                            readStatus.setReadAt(Instant.now());
                            readStatusRepository.save(readStatus);
                            // 鏇存柊 Redis 鏈璁℃暟
                            unreadCountService.decrement(userId, notification.getType());
                        }
                );
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(UUID userId, Integer type) {
        readStatusRepository.markAllAsRead(userId, type);
        // 鍏ㄩ儴宸茶鍚庨噸缃?Redis 璁℃暟
        unreadCountService.reset(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOTIFICATION_NOT_FOUND));
        if (userId.equals(notification.getSenderId())) {
            throw new BusinessException(ErrorCodes.NOTIFICATION_RECALL_FORBIDDEN);
        }
        notificationTargetRepository.markDeleted(notificationId, userId);
        // 濡傛灉鏈锛屽厛閫掑噺璁℃暟
        readStatusRepository.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresentOrElse(
                        readStatus -> {
                            // 宸茶锛屼笉褰卞搷璁℃暟
                        },
                        () -> unreadCountService.decrement(userId, notification.getType())
                );
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAllNotifications(UUID userId, Integer type) {
        notificationTargetRepository.markAllDeleted(userId, type);
        // 鍒犻櫎鍏ㄩ儴鍚庨噸缃?Redis 璁℃暟
        unreadCountService.reset(userId);
    }

    /**
     * 鎾ゅ洖閫氱煡锛堝彂閫佽€呮垨绠＄悊鍛樺彲鎿嶄綔锛屽鎵€鏈夋帴鏀惰€呯敓鏁堬級
     */
    @Transactional(rollbackFor = Exception.class)
    public void recallNotification(UUID notificationId, UUID senderId, Integer role) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOTIFICATION_NOT_FOUND));
        // 闈炵鐞嗗憳鍙兘鎾ゅ洖鑷繁鍙戝嚭鐨勯€氱煡
        if (!SecurityUtils.isAdmin(role)) {
            if (!senderId.equals(notification.getSenderId())) {
                throw new BusinessException(ErrorCodes.NOTIFICATION_RECALL_FORBIDDEN);
            }
        }
        notificationRepository.markDeleted(notificationId);
    }

    private void pushNotification(TargetType targetType, UUID senderId, List<UUID> userIds, NotificationVO notification) {
        if (targetType == TargetType.USER) {
            userIds.stream()
                    .distinct()
                    .forEach(userId -> sseEmitter.sendToUser(userId, notification));
            return;
        }

        sseEmitter.broadcastExcept(senderId, notification);
    }

    private NotificationVO toNotificationVO(Notification notification, boolean read) {
        return new NotificationVO(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getSenderId(),
                notification.getTargetType(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                read,
                null
        );
    }
}
