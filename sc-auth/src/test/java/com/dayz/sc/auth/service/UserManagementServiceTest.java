package com.dayz.sc.auth.service;

import com.dayz.sc.auth.model.dto.CompleteOnboardingRequest;
import com.dayz.sc.auth.model.entity.Student;
import com.dayz.sc.auth.model.entity.Teacher;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import com.dayz.sc.auth.model.vo.LoginResponseVO;
import com.dayz.sc.auth.repository.StudentRepository;
import com.dayz.sc.auth.repository.TeacherRepository;
import com.dayz.sc.auth.repository.UserAccountRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
