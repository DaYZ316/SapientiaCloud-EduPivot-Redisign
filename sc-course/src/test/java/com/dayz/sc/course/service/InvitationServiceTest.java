package com.dayz.sc.course.service;

import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.model.dto.InviteAssistantRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.CourseInvitation;
import com.dayz.sc.course.model.enums.InvitationStatus;
import com.dayz.sc.course.repository.CourseInvitationRepository;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private CourseInvitationRepository invitationRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private AuthInternalClient authInternalClient;

    @Mock
    private StorageInternalClient storageInternalClient;

    @Mock
    private CourseEventPublisher courseEventPublisher;

    @Captor
    private ArgumentCaptor<CourseInvitation> invitationCaptor;

    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
                invitationRepository,
                courseRepository,
                courseTeacherRepository,
                authInternalClient,
                storageInternalClient,
                courseEventPublisher
        );
    }

    @Test
    void invite_shouldAllowPrimaryTeacherToInviteAssistant() {
        UUID courseId = UUID.randomUUID();
        UUID primaryTeacherId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();
        Course course = course(courseId, primaryTeacherId);

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        when(courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, inviteeId)).thenReturn(false);
        when(invitationRepository.existsPendingByCourseIdAndInviteeId(courseId, inviteeId)).thenReturn(false);
        when(authInternalClient.getUsersBasicInfo(any()))
                .thenAnswer(invocation -> ApiResponse.ok(
                        ((List<UUID>) invocation.getArgument(0)).stream()
                                .map(id -> new UserBasicInfo(id, "Teacher " + id, null, 2))
                                .toList()
                ));

        invitationService.invite(new InviteAssistantRequest(courseId, inviteeId, "join us"), primaryTeacherId, 2);

        verify(invitationRepository).save(invitationCaptor.capture());
        CourseInvitation savedInvitation = invitationCaptor.getValue();
        assertThat(savedInvitation.getCourseId()).isEqualTo(courseId);
        assertThat(savedInvitation.getInviterId()).isEqualTo(primaryTeacherId);
        assertThat(savedInvitation.getInviteeId()).isEqualTo(inviteeId);
        assertThat(savedInvitation.getStatus()).isEqualTo(InvitationStatus.PENDING.getCode());
    }

    @Test
    void invite_shouldRejectCourseAssistant() {
        UUID courseId = UUID.randomUUID();
        UUID primaryTeacherId = UUID.randomUUID();
        UUID assistantId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, primaryTeacherId)));

        assertThatThrownBy(() -> invitationService.invite(
                new InviteAssistantRequest(courseId, inviteeId, null), assistantId, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有主讲教师能邀请助教");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void invite_shouldRejectUserWhoIsNotCourseTeacherOrAdmin() {
        UUID courseId = UUID.randomUUID();
        UUID primaryTeacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course(courseId, primaryTeacherId)));

        assertThatThrownBy(() -> invitationService.invite(
                new InviteAssistantRequest(courseId, inviteeId, null), studentId, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有主讲教师能邀请助教");
        verify(invitationRepository, never()).save(any());
    }

    private Course course(UUID courseId, UUID teacherId) {
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(teacherId);
        course.setTitle("Course");
        return course;
    }
}
