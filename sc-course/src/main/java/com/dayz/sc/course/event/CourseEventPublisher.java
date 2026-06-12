package com.dayz.sc.course.event;

import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.events.course.CourseCreatedEvent;
import com.dayz.sc.common.events.course.CourseDeletedEvent;
import com.dayz.sc.course.model.entity.Course;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.dayz.sc.common.util.UuidV7Generator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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
                UuidV7Generator.generate(), course.getId(), course.getTitle(), course.getTeacherId(), course.getSemester());
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
                UuidV7Generator.generate(), course.getId(), course.getTitle(), course.getTeacherId());
        kafkaTemplate.send(KafkaTopicConstants.COURSE_EVENTS, course.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish CourseDeletedEvent for course {}", course.getId(), ex);
                    } else {
                        log.debug("Published CourseDeletedEvent for course {}", course.getId());
                    }
                });
    }
}
