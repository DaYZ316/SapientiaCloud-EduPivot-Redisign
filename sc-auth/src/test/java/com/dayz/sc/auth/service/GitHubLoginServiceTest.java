package com.dayz.sc.auth.service;

import com.dayz.sc.auth.client.github.GitHubOauthClient;
import com.dayz.sc.auth.client.github.GitHubUserClient;
import com.dayz.sc.auth.client.github.dto.GitHubEmailResponse;
import com.dayz.sc.auth.client.github.dto.GitHubTokenResponse;
import com.dayz.sc.auth.client.github.dto.GitHubUserResponse;
import com.dayz.sc.auth.config.GitHubOauthProperties;
import com.dayz.sc.auth.model.dto.GitHubLoginRequest;
import com.dayz.sc.auth.model.dto.OauthUserInfo;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import com.dayz.sc.auth.repository.StudentRepository;
import com.dayz.sc.auth.repository.TeacherRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.security.service.JwtTokenService;
import com.dayz.sc.common.security.token.RefreshTokenService;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubLoginServiceTest {

    @Mock
    private GitHubOauthClient gitHubOauthClient;

    @Mock
    private GitHubUserClient gitHubUserClient;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private TeacherRepository teacherRepository;

    @Test
    void login_shouldRequestJsonFromGitHubTokenEndpoint() {
        UUID userId = UUID.randomUUID();
        GitHubOauthProperties properties = properties();
        GitHubLoginService service = service(properties);
        when(gitHubOauthClient.exchangeCode(
                eq(MediaType.APPLICATION_JSON_VALUE),
                eq("github-client-id"),
                eq("github-client-secret"),
                eq("github-code"),
                eq("http://localhost:5173/login"),
                isNull()
        )).thenReturn(new GitHubTokenResponse("github-access-token", "read:user,user:email", "bearer", null, null, null));
        when(gitHubUserClient.getUser("Bearer github-access-token", "application/vnd.github+json", properties.getApiVersion()))
                .thenReturn(new GitHubUserResponse(123L, "octocat", "Octo Cat", null, "https://example.com/avatar.png"));
        when(gitHubUserClient.listEmails("Bearer github-access-token", "application/vnd.github+json", properties.getApiVersion(), 100, 1))
                .thenReturn(List.of(new GitHubEmailResponse("octocat@example.com", true, true, "public")));
        when(userAccountService.loginWithOauth(any(OauthUserInfo.class), eq("127.0.0.1")))
                .thenReturn(new UserAccountService.AuthenticatedUser(user(userId), List.of(OauthProvider.GITHUB)));
        when(jwtTokenService.createAccessToken(eq(userId.toString()), any())).thenReturn("access-token");
        when(jwtTokenService.getAccessTokenTtlSeconds()).thenReturn(1800L);
        when(refreshTokenService.createRefreshToken(eq(userId.toString()), isNull())).thenReturn("refresh-token");
        when(studentRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(teacherRepository.findByUserId(userId)).thenReturn(Optional.empty());

        service.login(new GitHubLoginRequest("github-code", "http://localhost:5173/login", null), "127.0.0.1");

        ArgumentCaptor<OauthUserInfo> userInfoCaptor = ArgumentCaptor.forClass(OauthUserInfo.class);
        verify(userAccountService).loginWithOauth(userInfoCaptor.capture(), eq("127.0.0.1"));
        assertThat(userInfoCaptor.getValue().provider()).isEqualTo(OauthProvider.GITHUB);
        assertThat(userInfoCaptor.getValue().email()).isEqualTo("octocat@example.com");
    }

    @Test
    void login_shouldExposeWrappedGitHubTokenError() {
        GitHubOauthProperties properties = properties();
        GitHubLoginService service = service(properties);
        FeignException cause = feignException(400, "{\"error\":\"bad_verification_code\"}");
        when(gitHubOauthClient.exchangeCode(
                eq(MediaType.APPLICATION_JSON_VALUE),
                eq("github-client-id"),
                eq("github-client-secret"),
                eq("github-code"),
                eq("http://localhost:5173/login"),
                isNull()
        )).thenThrow(new NoFallbackAvailableException("No fallback available.", cause));

        assertThatThrownBy(() -> service.login(new GitHubLoginRequest("github-code", "http://localhost:5173/login", null), "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("GitHub 授权码换取令牌失败: HTTP 400")
                .hasMessageContaining("bad_verification_code");
    }

    private GitHubLoginService service(GitHubOauthProperties properties) {
        return new GitHubLoginService(
                gitHubOauthClient,
                gitHubUserClient,
                properties,
                jwtTokenService,
                userAccountService,
                refreshTokenService,
                studentRepository,
                teacherRepository
        );
    }

    private GitHubOauthProperties properties() {
        GitHubOauthProperties properties = new GitHubOauthProperties();
        properties.setClientId("github-client-id");
        properties.setClientSecret("github-client-secret");
        properties.setRedirectUri("http://localhost:5173/login");
        return properties;
    }

    private FeignException feignException(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "https://github.com/login/oauth/access_token",
                Map.of(),
                null,
                StandardCharsets.UTF_8
        );
        Response response = Response.builder()
                .status(status)
                .reason("Bad Request")
                .request(request)
                .body(body, StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("GitHubOauthClient#exchangeCode", response);
    }

    private User user(UUID id) {
        Instant now = Instant.parse("2026-06-26T00:00:00Z");
        User user = new User();
        user.setId(id);
        user.setEmail("octocat@example.com");
        user.setEmailVerified(true);
        user.setDisplayName("Octo Cat");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setLocale("zh-CN");
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);
        user.setCreatedProvider(OauthProvider.GITHUB);
        user.setLastLoginProvider(OauthProvider.GITHUB);
        user.setLoginCount(1L);
        user.setTheme("system");
        user.setNotificationEnabled(true);
        return user;
    }
}
