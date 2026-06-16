package com.dayz.sc.course.event;

import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.events.course.CourseCreatedEvent;
import com.dayz.sc.common.events.course.CourseDeletedEvent;
import com.dayz.sc.common.events.course.CourseStatusChangedEvent;
import com.dayz.sc.common.events.course.EnrollmentChangedEvent;
import com.dayz.sc.common.events.course.InvitationChangedEvent;
import com.dayz.sc.course.model.entity.Course;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.dayz.sc.common.util.UuidV7Generator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 事件发布者
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseEventPublisher {

    private final ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

    public void publishCourseCreated(Course course) {
        if (course == null || course.getId() == null) {
            log.warn("Cannot publish CourseCreatedEvent: course or courseId is null");
            return;
        }
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Skip CourseCreatedEvent for course {}: KafkaTemplate is unavailable", course.getId());
            return;
        }
        CourseCreatedEvent event = new CourseCreatedEvent(
                UuidV7Generator.generate(), course.getId(), course.getTitle(), course.getTeacherId(), course.getSemester(),
                "COURSE_CREATED", Instant.now(), "sc-course");
        kafkaTemplate.send(KafkaTopicConstants.COURSE_EVENTS, course.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CourseCreatedEvent for course {}", course.getId(), ex);
                    } else {
                        log.debug("Published CourseCreatedEvent for course {}", course.getId());
                    }
                });
    }

    public void publishCourseDeleted(Course course) {
        if (course == null || course.getId() == null) {
            log.warn("Cannot publish CourseDeletedEvent: course or courseId is null");
            return;
        }
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Skip CourseDeletedEvent for course {}: KafkaTemplate is unavailable", course.getId());
            return;
        }
        CourseDeletedEvent event = new CourseDeletedEvent(
                UuidV7Generator.generate(), course.getId(), course.getTitle(), course.getTeacherId(),
                "COURSE_DELETED", Instant.now(), "sc-course");
        kafkaTemplate.send(KafkaTopicConstants.COURSE_EVENTS, course.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CourseDeletedEvent for course {}", course.getId(), ex);
                    } else {
                        log.debug("Published CourseDeletedEvent for course {}", course.getId());
                    }
                });
    }

    public void publishCourseStatusChanged(UUID courseId, String courseTitle, UUID teacherId, String action) {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Skip CourseStatusChangedEvent for course {}: KafkaTemplate is unavailable", courseId);
            return;
        }
        CourseStatusChangedEvent event = new CourseStatusChangedEvent(
                UuidV7Generator.generate(), courseId, courseTitle, teacherId, action,
                "COURSE_STATUS_CHANGED", Instant.now(), "sc-course");
        kafkaTemplate.send(KafkaTopicConstants.COURSE_EVENTS, courseId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CourseStatusChangedEvent for course {}", courseId, ex);
                    } else {
                        log.debug("Published CourseStatusChangedEvent for course {}", courseId);
                    }
                });
    }

    public void publishEnrollmentChanged(UUID courseId, String courseTitle,
                                         UUID studentId, String studentName,
                                         UUID teacherId, String action) {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Skip EnrollmentChangedEvent for course {}: KafkaTemplate is unavailable", courseId);
            return;
        }
        EnrollmentChangedEvent event = new EnrollmentChangedEvent(
                UuidV7Generator.generate(), courseId, courseTitle,
                studentId, studentName, teacherId, action,
                "ENROLLMENT_CHANGED", Instant.now(), "sc-course");
        kafkaTemplate.send(KafkaTopicConstants.COURSE_EVENTS, courseId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish EnrollmentChangedEvent for course {}", courseId, ex);
                    } else {
                        log.debug("Published EnrollmentChangedEvent for course {}", courseId);
                    }
                });
    }

    public void publishInvitationChanged(UUID courseId, String courseTitle,
                                         UUID inviterId, String inviterName,
                                         UUID inviteeId, String inviteeName,
                                         String action) {
        KafkaTemplate<String, Object> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            log.warn("Skip InvitationChangedEvent for course {}: KafkaTemplate is unavailable", courseId);
            return;
        }
        InvitationChangedEvent event = new InvitationChangedEvent(
                UuidV7Generator.generate(), courseId, courseTitle,
                inviterId, inviterName, inviteeId, inviteeName, action,
                "INVITATION_CHANGED", Instant.now(), "sc-course");
        kafkaTemplate.send(KafkaTopicConstants.COURSE_EVENTS, courseId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InvitationChangedEvent for course {}", courseId, ex);
                    } else {
                        log.debug("Published InvitationChangedEvent for course {}", courseId);
                    }
                });
    }
}