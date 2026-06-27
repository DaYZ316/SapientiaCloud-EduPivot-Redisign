package com.dayz.sc.notification.kafka;

import com.dayz.sc.common.events.course.*;
import com.dayz.sc.common.redis.kafka.KafkaIdempotencyGuard;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.notification.model.entity.Notification;
import com.dayz.sc.notification.model.enums.NotificationType;
import com.dayz.sc.notification.model.enums.TargetType;
import com.dayz.sc.notification.model.vo.NotificationVO;
import com.dayz.sc.notification.repository.NotificationRepository;
import com.dayz.sc.notification.repository.NotificationTargetRepository;
import com.dayz.sc.notification.sse.NotificationSseEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 事件消费者
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventConsumer {

    private static final String GROUP_ID = "sc-notification";
    private static final String ACTION_ENROLLED = "ENROLLED";

    private final NotificationRepository notificationRepository;
    private final NotificationTargetRepository notificationTargetRepository;
    private final NotificationSseEmitter sseEmitter;
    private final KafkaIdempotencyGuard idempotencyGuard;

    @KafkaListener(topics = "#{T(com.dayz.sc.common.events.config.KafkaTopicConstants).COURSE_EVENTS}", groupId = "sc-notification")
    public void onCourseEvent(Object event, Acknowledgment ack) {
        UUID eventId = eventId(event);
        if (eventId != null && !idempotencyGuard.tryAcquire(GROUP_ID, eventId)) {
            log.info("Duplicate course event skipped: {}", eventId);
            ack.acknowledge();
            return;
        }
        try {
            switch (event) {
                case CourseCreatedEvent e -> handleCourseCreated(e);
                case CourseDeletedEvent e -> handleCourseDeleted(e);
                case CourseStatusChangedEvent e -> handleCourseStatusChanged(e);
                case EnrollmentChangedEvent e -> handleEnrollmentChanged(e);
                case InvitationChangedEvent e -> handleInvitationChanged(e);
                default -> log.warn("Unknown course event type: {}", event.getClass().getSimpleName());
            }
            ack.acknowledge();
        } catch (RuntimeException exception) {
            if (eventId != null) {
                idempotencyGuard.release(GROUP_ID, eventId);
            }
            throw exception;
        }
    }

    private UUID eventId(Object event) {
        return switch (event) {
            case CourseCreatedEvent e -> e.eventId();
            case CourseDeletedEvent e -> e.eventId();
            case CourseStatusChangedEvent e -> e.eventId();
            case EnrollmentChangedEvent e -> e.eventId();
            case InvitationChangedEvent e -> e.eventId();
            default -> null;
        };
    }

    private void handleCourseCreated(CourseCreatedEvent event) {
        log.info("Received CourseCreatedEvent for course: {}", event.courseId());
        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.SYSTEM.getCode());
        notification.setTitle("新课程发布");
        notification.setContent("课程「" + event.title() + "」已发布");
        notification.setSenderId(event.teacherId());
        notification.setTargetType(TargetType.ALL.getCode());

        notificationRepository.save(notification);

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.broadcast(vo);
    }

    private void handleCourseDeleted(CourseDeletedEvent event) {
        log.info("Received CourseDeletedEvent for course: {}", event.courseId());
        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.SYSTEM.getCode());
        notification.setTitle("课程已删除");
        notification.setContent("课程「" + event.title() + "」已被删除");
        notification.setSenderId(event.teacherId());
        notification.setTargetType(TargetType.ALL.getCode());

        notificationRepository.save(notification);

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.broadcast(vo);
    }

    private void handleCourseStatusChanged(CourseStatusChangedEvent event) {
        log.info("Received CourseStatusChangedEvent for course: {} action: {}", event.courseId(), event.action());
        String title;
        String content;
        switch (event.action()) {
            case "PUBLISHED" -> {
                title = "课程已发布";
                content = "课程「" + event.courseTitle() + "」已发布，快来查看吧";
            }
            case "DRAFT" -> {
                title = "课程已设为草稿";
                content = "课程「" + event.courseTitle() + "」已设置为草稿";
            }
            default -> {
                title = "课程已归档";
                content = "课程「" + event.courseTitle() + "」已归档";
            }
        }

        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.TEACHING.getCode());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setSenderId(event.teacherId());
        notification.setTargetType(TargetType.USER.getCode());

        notificationRepository.save(notification);
        // 课程状态变更通知发送给主讲教师
        notificationTargetRepository.saveAll(List.of(notification.getId()), event.teacherId());

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.sendToUser(event.teacherId(), vo);
    }

    private void handleEnrollmentChanged(EnrollmentChangedEvent event) {
        log.info("Received EnrollmentChangedEvent for course: {} action: {}", event.courseId(), event.action());
        String title;
        String content;
        if (ACTION_ENROLLED.equals(event.action())) {
            title = "学生选课通知";
            content = "学生「" + event.studentName() + "」已加入课程「" + event.courseTitle() + "」";
        } else {
            title = "学生退课通知";
            content = "学生「" + event.studentName() + "」已退出课程「" + event.courseTitle() + "」";
        }

        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.TEACHING.getCode());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setSenderId(event.studentId());
        notification.setTargetType(TargetType.USER.getCode());

        notificationRepository.save(notification);
        // 选课通知发送给课程主讲教师
        notificationTargetRepository.saveAll(List.of(notification.getId()), event.teacherId());

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.sendToUser(event.teacherId(), vo);
    }

    private void handleInvitationChanged(InvitationChangedEvent event) {
        log.info("Received InvitationChangedEvent for course: {} action: {}", event.courseId(), event.action());
        String title;
        String content;
        UUID targetUserId;
        UUID senderId;

        switch (event.action()) {
            case "INVITED" -> {
                title = "助教邀请";
                content = "教师「" + event.inviterName() + "」邀请您担任课程「" + event.courseTitle() + "」的助教";
                targetUserId = event.inviteeId();
                senderId = event.inviterId();
            }
            case "ACCEPTED" -> {
                title = "邀请已接受";
                content = "助教「" + event.inviteeName() + "」已接受您在课程「" + event.courseTitle() + "」的助教邀请";
                targetUserId = event.inviterId();
                senderId = event.inviteeId();
            }
            case "DECLINED" -> {
                title = "邀请已拒绝";
                content = "助教「" + event.inviteeName() + "」已拒绝您在课程「" + event.courseTitle() + "」的助教邀请";
                targetUserId = event.inviterId();
                senderId = event.inviteeId();
            }
            default -> {
                log.warn("Unknown invitation action: {}", event.action());
                return;
            }
        }

        Notification notification = new Notification();
        notification.setId(UuidV7Generator.generate());
        notification.setType(NotificationType.TEACHING.getCode());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setSenderId(senderId);
        notification.setTargetType(TargetType.USER.getCode());

        notificationRepository.save(notification);
        notificationTargetRepository.saveAll(List.of(notification.getId()), targetUserId);

        NotificationVO vo = toNotificationVO(notification);
        sseEmitter.sendToUser(targetUserId, vo);
    }

    private NotificationVO toNotificationVO(Notification notification) {
        return new NotificationVO(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getSenderId(),
                notification.getTargetType(),
                notification.getCreatedAt(),
                notification.getUpdatedAt(),
                false,
                null
        );
    }
}
