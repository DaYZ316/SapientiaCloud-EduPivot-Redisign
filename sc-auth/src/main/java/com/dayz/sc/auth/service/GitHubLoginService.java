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
import com.dayz.sc.auth.model.vo.LoginResponseVO;
import com.dayz.sc.auth.model.vo.StudentInfoVO;
import com.dayz.sc.auth.model.vo.TeacherInfoVO;
import com.dayz.sc.auth.model.vo.UserProfileVO;
import com.dayz.sc.auth.repository.StudentRepository;
import com.dayz.sc.auth.repository.TeacherRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.security.service.JwtTokenService;
import com.dayz.sc.common.security.token.RefreshTokenService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 处理 GitHub OAuth 授权码登录并签发 JWT 访问令牌
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Service
@RequiredArgsConstructor
public class GitHubLoginService {
    private static final String GITHUB_JSON = "application/vnd.github+json";
    private static final String TOKEN_JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE = "Bearer";
    private static final int EMAIL_PAGE_SIZE = 100;
    private static final int FIRST_PAGE = 1;
    private static final int HTTP_FORBIDDEN = 403;
    private static final int HTTP_NOT_FOUND = 404;

    private final GitHubOauthClient gitHubOauthClient;
    private final GitHubUserClient gitHubUserClient;
    private final GitHubOauthProperties gitHubOauthProperties;
    private final JwtTokenService jwtTokenService;
    private final UserAccountService userAccountService;
    private final RefreshTokenService refreshTokenService;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    public LoginResponseVO login(GitHubLoginRequest request, String clientIp) {
        assertGitHubConfigured();
        String redirectUri = resolveRedirectUri(request);

        GitHubTokenResponse token;
        try {
            token = gitHubOauthClient.exchangeCode(
                    TOKEN_JSON,
                    gitHubOauthProperties.getClientId(),
                    gitHubOauthProperties.getClientSecret(),
                    request.code(),
                    redirectUri,
                    normalize(request.codeVerifier())
            );
        } catch (FeignException | NoFallbackAvailableException ex) {
            throw tokenExchangeFailed(ex);
        }

        if (token == null || !token.successful()) {
            String message = token == null ? "GitHub 登录失败" : "GitHub 登录失败: " + token.errorDescription();
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, message);
        }

        String authorization = BEARER_PREFIX + token.accessToken();
        GitHubUserResponse userInfo;
        try {
            userInfo = gitHubUserClient.getUser(authorization, GITHUB_JSON, gitHubOauthProperties.getApiVersion());
        } catch (FeignException | NoFallbackAvailableException ex) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 用户信息读取失败");
        }
        if (userInfo == null || userInfo.id() == null) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 用户信息缺少 id");
        }

        GitHubEmailResponse email = resolvePrimaryEmail(authorization, userInfo);
        UserAccountService.AuthenticatedUser authenticatedUser = userAccountService.loginWithOauth(toOauthUserInfo(userInfo, email), clientIp);
        User user = authenticatedUser.user();
        UserProfileVO profile = toUserProfile(authenticatedUser);

        String accessToken = jwtTokenService.createAccessToken(user.getId().toString(), buildClaims(user));
        String refreshToken = refreshTokenService.createRefreshToken(user.getId().toString(), user.getRole());

        return new LoginResponseVO(accessToken, refreshToken, TOKEN_TYPE, jwtTokenService.getAccessTokenTtlSeconds(), profile);
    }

    private GitHubEmailResponse resolvePrimaryEmail(String authorization, GitHubUserResponse userInfo) {
        List<GitHubEmailResponse> emails = List.of();
        try {
            emails = gitHubUserClient.listEmails(
                    authorization,
                    GITHUB_JSON,
                    gitHubOauthProperties.getApiVersion(),
                    EMAIL_PAGE_SIZE,
                    FIRST_PAGE
            );
        } catch (FeignException ex) {
            if (ex.status() != HTTP_FORBIDDEN && ex.status() != HTTP_NOT_FOUND) {
                throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 邮箱信息读取失败");
            }
        } catch (NoFallbackAvailableException ex) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub 邮箱信息读取失败");
        }

        return selectEmail(emails)
                .orElseGet(() -> StringUtils.hasText(userInfo.email())
                        ? new GitHubEmailResponse(userInfo.email(), true, false, null)
                        : null);
    }

    private Optional<GitHubEmailResponse> selectEmail(List<GitHubEmailResponse> emails) {
        if (emails == null || emails.isEmpty()) {
            return Optional.empty();
        }

        return emails.stream()
                .filter(email -> StringUtils.hasText(email.email()))
                .filter(email -> Boolean.TRUE.equals(email.verified()))
                .max(Comparator.comparing((GitHubEmailResponse email) -> Boolean.TRUE.equals(email.primary()))
                        .thenComparing(email -> StringUtils.hasText(email.visibility())));
    }

    private OauthUserInfo toOauthUserInfo(GitHubUserResponse userInfo, GitHubEmailResponse email) {
        return new OauthUserInfo(
                OauthProvider.GITHUB,
                userInfo.id().toString(),
                userInfo.login(),
                email == null ? null : email.email(),
                email != null && Boolean.TRUE.equals(email.verified()),
                resolveDisplayName(userInfo),
                userInfo.avatarUrl(),
                null
        );
    }

    private UserProfileVO toUserProfile(UserAccountService.AuthenticatedUser authenticatedUser) {
        User user = authenticatedUser.user();
        StudentInfoVO studentInfo = loadStudentInfo(user.getId());
        TeacherInfoVO teacherInfo = loadTeacherInfo(user.getId());

        return new UserProfileVO(
                user.getId(),
                user.getEmail(),
                user.getEmailVerified(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getAvatarFileId(),
                user.getLocale(),
                user.getStatus(),
                user.getPhone(),
                user.getBio(),
                user.getGender(),
                user.getBirthday(),
                user.getTheme(),
                user.getNotificationEnabled() != null ? user.getNotificationEnabled() : true,
                user.getCreatedProvider(),
                user.getCreatedIp(),
                user.getLastLoginProvider(),
                user.getLastLoginIp(),
                user.getLoginCount(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt(),
                authenticatedUser.linkedProviders(),
                user.getRole(),
                studentInfo,
                teacherInfo
        );
    }

    private StudentInfoVO loadStudentInfo(UUID userId) {
        return studentRepository.findByUserId(userId)
                .map(student -> new StudentInfoVO(
                        student.getId(),
                        student.getStudentNo(),
                        student.getGrade(),
                        student.getMajor(),
                        student.getSchool()
                ))
                .orElse(null);
    }

    private TeacherInfoVO loadTeacherInfo(UUID userId) {
        return teacherRepository.findByUserId(userId)
                .map(teacher -> new TeacherInfoVO(
                        teacher.getId(),
                        teacher.getEmployeeNo(),
                        teacher.getDepartment(),
                        teacher.getTitle(),
                        teacher.getSchool()
                ))
                .orElse(null);
    }

    /**
     * 精简 JWT claims：仅包含 userId 和 role
     */
    private Map<String, Object> buildClaims(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("profileComplete", user.getRole() != null && StringUtils.hasText(user.getDisplayName()));
        if (user.getRole() != null) {
            claims.put("role", user.getRole());
        }
        return claims;
    }

    private String resolveDisplayName(GitHubUserResponse userInfo) {
        return StringUtils.hasText(userInfo.name()) ? userInfo.name() : userInfo.login();
    }

    private BusinessException tokenExchangeFailed(Throwable exception) {
        return new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED,
                "GitHub 授权码换取令牌失败" + describeUpstreamException(exception));
    }

    private String describeUpstreamException(Throwable exception) {
        FeignException feignException = findFeignException(exception);
        if (feignException != null) {
            return describeFeignException(feignException);
        }

        String message = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String normalizedMessage = message.replaceAll("\\s+", " ").trim();
        if (normalizedMessage.length() > 180) {
            normalizedMessage = normalizedMessage.substring(0, 180) + "...";
        }
        return ": " + normalizedMessage;
    }

    private FeignException findFeignException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof FeignException feignException) {
                return feignException;
            }
            current = current.getCause();
        }
        return null;
    }

    private String describeFeignException(FeignException exception) {
        String body = exception.contentUTF8();
        if (!StringUtils.hasText(body)) {
            return ": HTTP " + exception.status();
        }
        String normalizedBody = body.replaceAll("\\s+", " ").trim();
        if (normalizedBody.length() > 180) {
            normalizedBody = normalizedBody.substring(0, 180) + "...";
        }
        return ": HTTP " + exception.status() + " " + normalizedBody;
    }

    private void assertGitHubConfigured() {
        if (!StringUtils.hasText(gitHubOauthProperties.getClientId())
                || !StringUtils.hasText(gitHubOauthProperties.getClientSecret())) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub OAuth clientId/clientSecret 未配置");
        }
    }

    private String resolveRedirectUri(GitHubLoginRequest request) {
        String redirectUri = StringUtils.hasText(request.redirectUri())
                ? request.redirectUri()
                : gitHubOauthProperties.getRedirectUri();
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException(ErrorCodes.GITHUB_LOGIN_FAILED, "GitHub OAuth redirectUri 未配置");
        }
        return redirectUri;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
