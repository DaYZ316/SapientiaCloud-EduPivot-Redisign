package com.dayz.sc.course.event;

import com.dayz.sc.common.events.config.KafkaTopicConstants;
import com.dayz.sc.common.events.course.*;
import com.dayz.sc.course.model.entity.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CourseEventPublisherTest 相关定义
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@ExtendWith(MockitoExtension.class)
class CourseEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider;

    @Captor
    private ArgumentCaptor<CourseCreatedEvent> courseCreatedEventCaptor;

    @Captor
    private ArgumentCaptor<CourseDeletedEvent> courseDeletedEventCaptor;

    @Captor
    private ArgumentCaptor<CourseStatusChangedEvent> courseStatusChangedEventCaptor;

    @Captor
    private ArgumentCaptor<EnrollmentChangedEvent> enrollmentChangedEventCaptor;

    @Captor
    private ArgumentCaptor<InvitationChangedEvent> invitationChangedEventCaptor;

    private CourseEventPublisher courseEventPublisher;

    @BeforeEach
    void setUp() {
        lenient().when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        courseEventPublisher = new CourseEventPublisher(kafkaTemplateProvider);
    }

    @Test
    void publishCourseCreated_shouldSendTypedEvent() {
        // Given
        Course course = createCourse();
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(course.getId().toString()), any()))
                .thenReturn(future);

        // When
        courseEventPublisher.publishCourseCreated(course);

        // Then
        verify(kafkaTemplate).send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(course.getId().toString()), courseCreatedEventCaptor.capture());
        CourseCreatedEvent event = courseCreatedEventCaptor.getValue();
        assertThat(event.courseId()).isEqualTo(course.getId());
        assertThat(event.title()).isEqualTo(course.getTitle());
        assertThat(event.teacherId()).isEqualTo(course.getTeacherId());
        assertThat(event.eventType()).isEqualTo("COURSE_CREATED");
        assertThat(event.source()).isEqualTo("sc-course");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    void publishCourseDeleted_shouldSendTypedEvent() {
        // Given
        Course course = createCourse();
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(course.getId().toString()), any()))
                .thenReturn(future);

        // When
        courseEventPublisher.publishCourseDeleted(course);

        // Then
        verify(kafkaTemplate).send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(course.getId().toString()), courseDeletedEventCaptor.capture());
        CourseDeletedEvent event = courseDeletedEventCaptor.getValue();
        assertThat(event.courseId()).isEqualTo(course.getId());
        assertThat(event.title()).isEqualTo(course.getTitle());
        assertThat(event.teacherId()).isEqualTo(course.getTeacherId());
        assertThat(event.eventType()).isEqualTo("COURSE_DELETED");
        assertThat(event.source()).isEqualTo("sc-course");
    }

    @Test
    void publishCourseStatusChanged_shouldSendTypedEvent() {
        // Given
        UUID courseId = UUID.randomUUID();
        String courseTitle = "Test Course";
        UUID teacherId = UUID.randomUUID();
        String action = "PUBLISH";

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(courseId.toString()), any()))
                .thenReturn(future);

        // When
        courseEventPublisher.publishCourseStatusChanged(courseId, courseTitle, teacherId, action);

        // Then
        verify(kafkaTemplate).send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(courseId.toString()), courseStatusChangedEventCaptor.capture());
        CourseStatusChangedEvent event = courseStatusChangedEventCaptor.getValue();
        assertThat(event.courseId()).isEqualTo(courseId);
        assertThat(event.courseTitle()).isEqualTo(courseTitle);
        assertThat(event.teacherId()).isEqualTo(teacherId);
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.eventType()).isEqualTo("COURSE_STATUS_CHANGED");
        assertThat(event.source()).isEqualTo("sc-course");
    }

    @Test
    void publishEnrollmentChanged_shouldSendTypedEvent() {
        // Given
        UUID courseId = UUID.randomUUID();
        String courseTitle = "Test Course";
        UUID studentId = UUID.randomUUID();
        String studentName = "Student";
        UUID teacherId = UUID.randomUUID();
        String action = "ENROLL";

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(courseId.toString()), any()))
                .thenReturn(future);

        // When
        courseEventPublisher.publishEnrollmentChanged(courseId, courseTitle, studentId, studentName, teacherId, action);

        // Then
        verify(kafkaTemplate).send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(courseId.toString()), enrollmentChangedEventCaptor.capture());
        EnrollmentChangedEvent event = enrollmentChangedEventCaptor.getValue();
        assertThat(event.courseId()).isEqualTo(courseId);
        assertThat(event.courseTitle()).isEqualTo(courseTitle);
        assertThat(event.studentId()).isEqualTo(studentId);
        assertThat(event.studentName()).isEqualTo(studentName);
        assertThat(event.teacherId()).isEqualTo(teacherId);
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.eventType()).isEqualTo("ENROLLMENT_CHANGED");
        assertThat(event.source()).isEqualTo("sc-course");
    }

    @Test
    void publishInvitationChanged_shouldSendTypedEvent() {
        // Given
        UUID courseId = UUID.randomUUID();
        String courseTitle = "Test Course";
        UUID inviterId = UUID.randomUUID();
        String inviterName = "Inviter";
        UUID inviteeId = UUID.randomUUID();
        String inviteeName = "Invitee";
        String action = "INVITE";

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(courseId.toString()), any()))
                .thenReturn(future);

        // When
        courseEventPublisher.publishInvitationChanged(courseId, courseTitle, inviterId, inviterName, inviteeId, inviteeName, action);

        // Then
        verify(kafkaTemplate).send(eq(KafkaTopicConstants.COURSE_EVENTS), eq(courseId.toString()), invitationChangedEventCaptor.capture());
        InvitationChangedEvent event = invitationChangedEventCaptor.getValue();
        assertThat(event.courseId()).isEqualTo(courseId);
        assertThat(event.courseTitle()).isEqualTo(courseTitle);
        assertThat(event.inviterId()).isEqualTo(inviterId);
        assertThat(event.inviterName()).isEqualTo(inviterName);
        assertThat(event.inviteeId()).isEqualTo(inviteeId);
        assertThat(event.inviteeName()).isEqualTo(inviteeName);
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.eventType()).isEqualTo("INVITATION_CHANGED");
        assertThat(event.source()).isEqualTo("sc-course");
    }

    @Test
    void publishCourseCreated_shouldSkipWhenKafkaTemplateUnavailable() {
        // Given
        ObjectProvider<KafkaTemplate<String, Object>> nullProvider = mock(ObjectProvider.class);
        when(nullProvider.getIfAvailable()).thenReturn(null);
        CourseEventPublisher publisherWithNullKafka = new CourseEventPublisher(nullProvider);
        Course course = createCourse();

        // When
        publisherWithNullKafka.publishCourseCreated(course);

        // Then
        verifyNoInteractions(kafkaTemplate);
    }

    private Course createCourse() {
        Course course = new Course();
        course.setId(UUID.randomUUID());
        course.setTitle("Test Course");
        course.setTeacherId(UUID.randomUUID());
        course.setSemester("2026-Spring");
        return course;
    }
}
