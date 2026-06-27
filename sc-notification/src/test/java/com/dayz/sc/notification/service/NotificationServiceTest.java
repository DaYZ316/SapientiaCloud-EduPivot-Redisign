package com.dayz.sc.notification.service;

import com.dayz.sc.notification.model.entity.Notification;
import com.dayz.sc.notification.model.enums.NotificationType;
import com.dayz.sc.notification.repository.NotificationReadStatusRepository;
import com.dayz.sc.notification.repository.NotificationRepository;
import com.dayz.sc.notification.repository.NotificationTargetRepository;
import com.dayz.sc.notification.sse.NotificationSseEmitter;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Test
    void deleteNotification_shouldUpdateDbBeforeUnreadCache() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationReadStatusRepository readStatusRepository = mock(NotificationReadStatusRepository.class);
        NotificationTargetRepository notificationTargetRepository = mock(NotificationTargetRepository.class);
        UnreadCountService unreadCountService = mock(UnreadCountService.class);
        NotificationService service = new NotificationService(
                notificationRepository,
                readStatusRepository,
                notificationTargetRepository,
                mock(NotificationSseEmitter.class),
                unreadCountService);
        UUID notificationId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setSenderId(senderId);
        notification.setType(NotificationType.TEACHING.getCode());
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(readStatusRepository.findByNotificationIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        service.deleteNotification(notificationId, userId);

        var inOrder = inOrder(notificationTargetRepository, readStatusRepository, unreadCountService);
        inOrder.verify(notificationTargetRepository).markDeleted(notificationId, userId);
        inOrder.verify(readStatusRepository).findByNotificationIdAndUserId(notificationId, userId);
        inOrder.verify(unreadCountService).decrement(userId, notification.getType());
    }
}
