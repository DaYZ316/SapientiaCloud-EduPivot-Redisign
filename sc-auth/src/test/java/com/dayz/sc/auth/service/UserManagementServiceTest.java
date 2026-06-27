package com.dayz.sc.auth.service;

import com.dayz.sc.auth.model.dto.ChangePasswordRequest;
import com.dayz.sc.auth.model.dto.CompleteOnboardingRequest;
import com.dayz.sc.auth.model.dto.UpdateUserRequest;
import com.dayz.sc.auth.model.entity.Student;
import com.dayz.sc.auth.model.entity.Teacher;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import com.dayz.sc.auth.model.vo.LoginResponseVO;
import com.dayz.sc.auth.repository.StudentRepository;
import com.dayz.sc.auth.repository.TeacherRepository;
import com.dayz.sc.auth.repository.UserAccountRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.model.UserRole;
import com.dayz.sc.common.security.service.JwtTokenService;
import com.dayz.sc.common.security.token.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Test
    void completeOnboarding_shouldCreateStudentProfileAndReturnFreshTokens() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        UserManagementService service = newService();
        AtomicReference<Student> savedStudent = new AtomicReference<>();
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.findLinkedProviders(userId)).thenReturn(List.of(OauthProvider.GOOGLE));
        when(studentRepository.findByUserId(userId)).thenAnswer(invocation -> Optional.ofNullable(savedStudent.get()));
        when(teacherRepository.findByUserId(userId)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            savedStudent.set(invocation.getArgument(0));
            return null;
        }).when(studentRepository).save(any(Student.class));
        when(jwtTokenService.createAccessToken(eq(userId.toString()), any())).thenReturn("fresh-access-token");
        when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(1800L);
        when(refreshTokenService.createRefreshToken(userId.toString(), UserRole.STUDENT.getCode()))
                .thenReturn("fresh-refresh-token");

        LoginResponseVO response = service.completeOnboarding(
                userId,
                new CompleteOnboardingRequest(UserRole.STUDENT.getCode(), "  Ada Lovelace  ")
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(userAccountRepository).saveUser(userCaptor.capture());
        verify(studentRepository).save(studentCaptor.capture());
        verify(teacherRepository, never()).save(any(Teacher.class));
        verify(jwtTokenService).createAccessToken(eq(userId.toString()), claimsCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.STUDENT.getCode());
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(studentCaptor.getValue().getStudentNo()).startsWith("S");
        Map<String, Object> claims = claimsCaptor.getValue();
        assertThat(claims).containsEntry("role", UserRole.STUDENT.getCode());
        assertThat(claims).containsEntry("profileComplete", true);
        assertThat(response.accessToken()).isEqualTo("fresh-access-token");
        assertThat(response.refreshToken()).isEqualTo("fresh-refresh-token");
        assertThat(response.user().role()).isEqualTo(UserRole.STUDENT.getCode());
        assertThat(response.user().studentInfo()).isNotNull();
    }

    @Test
    void getAuthTokenState_shouldUseCurrentUserRoleAndProfileState() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        user.setRole(UserRole.TEACHER.getCode());
        user.setDisplayName("Grace Hopper");
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));

        UserManagementService.AuthTokenState state = newService().getAuthTokenState(userId);

        assertThat(state.role()).isEqualTo(UserRole.TEACHER.getCode());
        assertThat(state.profileComplete()).isTrue();
    }

    @Test
    void updateCurrentUser_shouldUpdateStudentProfileWhenUserIsStudent() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        user.setRole(UserRole.STUDENT.getCode());
        user.setDisplayName("Ada");
        Student student = student(userId);
        AtomicReference<Student> currentStudent = new AtomicReference<>(student);
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.findLinkedProviders(userId)).thenReturn(List.of(OauthProvider.GOOGLE));
        when(studentRepository.findByUserId(userId)).thenAnswer(invocation -> Optional.ofNullable(currentStudent.get()));
        when(teacherRepository.findByUserId(userId)).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            currentStudent.set(invocation.getArgument(0));
            return null;
        }).when(studentRepository).update(any(Student.class));

        var response = newService().updateCurrentUser(
                userId,
                new UpdateUserRequest(null, null, "  Ada Lovelace  ", null, null, null,
                        null, null, null, null, null, null, null,
                        new UpdateUserRequest.StudentInfoUpdate("  2026  ", "  AI  ", "  Engineering  "),
                        null)
        );

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).update(studentCaptor.capture());
        verify(teacherRepository, never()).update(any(Teacher.class));
        assertThat(user.getDisplayName()).isEqualTo("Ada Lovelace");
        assertThat(studentCaptor.getValue().getStudentNo()).isEqualTo("S20260001");
        assertThat(studentCaptor.getValue().getGrade()).isEqualTo("2026");
        assertThat(studentCaptor.getValue().getMajor()).isEqualTo("AI");
        assertThat(studentCaptor.getValue().getSchool()).isEqualTo("Engineering");
        assertThat(response.studentInfo()).isNotNull();
        assertThat(response.studentInfo().major()).isEqualTo("AI");
        assertThat(response.teacherInfo()).isNull();
    }

    @Test
    void updateCurrentUser_shouldUpdateTeacherProfileWhenUserIsTeacher() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        user.setRole(UserRole.TEACHER.getCode());
        user.setDisplayName("Grace");
        Teacher teacher = teacher(userId);
        AtomicReference<Teacher> currentTeacher = new AtomicReference<>(teacher);
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.findLinkedProviders(userId)).thenReturn(List.of(OauthProvider.GOOGLE));
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(teacherRepository.findByUserId(userId)).thenAnswer(invocation -> Optional.ofNullable(currentTeacher.get()));
        doAnswer(invocation -> {
            currentTeacher.set(invocation.getArgument(0));
            return null;
        }).when(teacherRepository).update(any(Teacher.class));

        var response = newService().updateCurrentUser(
                userId,
                new UpdateUserRequest(null, null, "  Grace Hopper  ", null, null, null,
                        null, null, null, null, null, null, null,
                        null,
                        new UpdateUserRequest.TeacherInfoUpdate("  Computer Science  ", "  Professor  ", "  Engineering  "))
        );

        ArgumentCaptor<Teacher> teacherCaptor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).update(teacherCaptor.capture());
        verify(studentRepository, never()).update(any(Student.class));
        assertThat(user.getDisplayName()).isEqualTo("Grace Hopper");
        assertThat(teacherCaptor.getValue().getEmployeeNo()).isEqualTo("T20260001");
        assertThat(teacherCaptor.getValue().getDepartment()).isEqualTo("Computer Science");
        assertThat(teacherCaptor.getValue().getTitle()).isEqualTo("Professor");
        assertThat(teacherCaptor.getValue().getSchool()).isEqualTo("Engineering");
        assertThat(response.teacherInfo()).isNotNull();
        assertThat(response.teacherInfo().department()).isEqualTo("Computer Science");
        assertThat(response.studentInfo()).isNull();
    }

    @Test
    void changeCurrentUserPassword_shouldRequireCurrentPasswordWhenPasswordExists() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        user.setPasswordHash("old-hash");
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.findLinkedProviders(userId)).thenReturn(List.of(OauthProvider.GOOGLE));
        when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(teacherRepository.findByUserId(userId)).thenReturn(Optional.empty());

        var response = newService().changeCurrentUserPassword(
                userId,
                new ChangePasswordRequest("old-password", "new-password")
        );

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userAccountRepository).saveUser(userCaptor.capture());
        verify(userAccountRepository).saveIdentity(identityCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("new-hash");
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(OauthProvider.LOCAL);
        assertThat(identityCaptor.getValue().getProviderUserId()).isEqualTo(userId.toString());
        assertThat(response.linkedProviders()).contains(OauthProvider.LOCAL);
    }

    @Test
    void changeCurrentUserPassword_shouldRejectWrongCurrentPassword() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        user.setPasswordHash("old-hash");
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.findLinkedProviders(userId)).thenReturn(List.of(OauthProvider.LOCAL));
        when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> newService().changeCurrentUserPassword(
                userId,
                new ChangePasswordRequest("wrong-password", "new-password")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userAccountRepository, never()).saveUser(any(User.class));
        verify(userAccountRepository, never()).saveIdentity(any(UserIdentity.class));
    }

    @Test
    void changeCurrentUserPassword_shouldSetInitialPasswordForVerifiedOauthUser() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.findLinkedProviders(userId))
                .thenReturn(List.of(OauthProvider.GOOGLE))
                .thenReturn(List.of(OauthProvider.GOOGLE))
                .thenReturn(List.of(OauthProvider.GOOGLE, OauthProvider.LOCAL));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(teacherRepository.findByUserId(userId)).thenReturn(Optional.empty());

        var response = newService().changeCurrentUserPassword(
                userId,
                new ChangePasswordRequest(null, "new-password")
        );

        ArgumentCaptor<UserIdentity> identityCaptor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userAccountRepository).saveIdentity(identityCaptor.capture());
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(OauthProvider.LOCAL);
        assertThat(response.linkedProviders()).contains(OauthProvider.LOCAL);
    }

    @Test
    void changeCurrentUserPassword_shouldRejectOauthUserWithoutVerifiedEmail() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        user.setEmailVerified(false);
        when(userAccountRepository.findUser(userId)).thenReturn(Optional.of(user));
        when(userAccountRepository.findLinkedProviders(userId)).thenReturn(List.of(OauthProvider.GOOGLE));

        assertThatThrownBy(() -> newService().changeCurrentUserPassword(
                userId,
                new ChangePasswordRequest(null, "new-password")
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Verified OAuth account is required");

        verify(userAccountRepository, never()).saveUser(any(User.class));
        verify(userAccountRepository, never()).saveIdentity(any(UserIdentity.class));
    }

    private UserManagementService newService() {
        return new UserManagementService(
                userAccountRepository,
                studentRepository,
                teacherRepository,
                passwordEncoder,
                null,
                null,
                jwtTokenService,
                refreshTokenService,
                Clock.fixed(Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private User user(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("oauth@example.com");
        user.setEmailVerified(true);
        user.setDisplayName(null);
        user.setLocale("zh-CN");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(Instant.parse("2026-06-26T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-06-26T00:00:00Z"));
        user.setLastLoginAt(Instant.parse("2026-06-26T00:00:00Z"));
        user.setCreatedProvider(OauthProvider.GOOGLE);
        user.setLastLoginProvider(OauthProvider.GOOGLE);
        user.setLoginCount(1L);
        user.setTheme("system");
        user.setNotificationEnabled(true);
        return user;
    }

    private Student student(UUID userId) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setUserId(userId);
        student.setStudentNo("S20260001");
        student.setCreatedAt(Instant.parse("2026-06-20T00:00:00Z"));
        student.setUpdatedAt(Instant.parse("2026-06-20T00:00:00Z"));
        return student;
    }

    private Teacher teacher(UUID userId) {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setUserId(userId);
        teacher.setEmployeeNo("T20260001");
        teacher.setCreatedAt(Instant.parse("2026-06-20T00:00:00Z"));
        teacher.setUpdatedAt(Instant.parse("2026-06-20T00:00:00Z"));
        return teacher;
    }
}
