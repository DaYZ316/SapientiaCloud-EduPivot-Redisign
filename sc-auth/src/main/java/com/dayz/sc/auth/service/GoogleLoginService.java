package com.dayz.sc.auth.service;

import com.dayz.sc.auth.client.google.GoogleOauthClient;
import com.dayz.sc.auth.client.google.GoogleUserInfoClient;
import com.dayz.sc.auth.client.google.dto.GoogleTokenResponse;
import com.dayz.sc.auth.client.google.dto.GoogleUserInfoResponse;
import com.dayz.sc.auth.config.GoogleOauthProperties;
import com.dayz.sc.auth.model.dto.GoogleLoginRequest;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 处理 Google OAuth2 授权码登录并签发 JWT 访问令牌
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Service
@RequiredArgsConstructor
public class GoogleLoginService {
    private static final String AUTHORIZATION_CODE = "authorization_code";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE = "Bearer";

    private final GoogleOauthClient googleOauthClient;
    private final GoogleUserInfoClient googleUserInfoClient;
    private final GoogleOauthProperties googleOauthProperties;
    private final JwtTokenService jwtTokenService;
    private final UserAccountService userAccountService;
    private final RefreshTokenService refreshTokenService;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    public LoginResponseVO login(GoogleLoginRequest request, String clientIp) {
        assertGoogleConfigured();
        String redirectUri = resolveRedirectUri(request);

        GoogleTokenResponse token = googleOauthClient.exchangeCode(
                request.code(),
                googleOauthProperties.getClientId(),
                googleOauthProperties.getClientSecret(),
                redirectUri,
                AUTHORIZATION_CODE
        );

        if (token == null || !token.successful()) {
            String message = token == null ? "Google 登录失败" : "Google 登录失败: " + token.errorDescription();
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, message);
        }

        GoogleUserInfoResponse userInfo = googleUserInfoClient.getUserInfo(BEARER_PREFIX + token.accessToken());
        if (userInfo == null || !StringUtils.hasText(userInfo.sub())) {
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, "Google 用户信息缺少 subject");
        }

        UserAccountService.AuthenticatedUser authenticatedUser = userAccountService.loginWithOauth(toOauthUserInfo(userInfo), clientIp);
        User user = authenticatedUser.user();
        UserProfileVO profile = toUserProfile(authenticatedUser);

        String accessToken = jwtTokenService.createAccessToken(user.getId().toString(), buildClaims(user));
        String refreshToken = refreshTokenService.createRefreshToken(user.getId().toString(), user.getRole());

        return new LoginResponseVO(accessToken, refreshToken, TOKEN_TYPE, jwtTokenService.getAccessTokenTtlSeconds(), profile);
    }

    private OauthUserInfo toOauthUserInfo(GoogleUserInfoResponse userInfo) {
        return new OauthUserInfo(
                OauthProvider.GOOGLE,
                userInfo.sub(),
                null,
                userInfo.email(),
                Boolean.TRUE.equals(userInfo.emailVerified()),
                userInfo.name(),
                userInfo.picture(),
                userInfo.locale()
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
        if (user.getRole() != null) {
            claims.put("role", user.getRole());
        }
        return claims;
    }

    private void assertGoogleConfigured() {
        if (!StringUtils.hasText(googleOauthProperties.getClientId())
                || !StringUtils.hasText(googleOauthProperties.getClientSecret())) {
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, "Google OAuth2 clientId/clientSecret 未配置");
        }
    }

    private String resolveRedirectUri(GoogleLoginRequest request) {
        String redirectUri = StringUtils.hasText(request.redirectUri())
                ? request.redirectUri()
                : googleOauthProperties.getRedirectUri();
        if (!StringUtils.hasText(redirectUri)) {
            throw new BusinessException(ErrorCodes.GOOGLE_LOGIN_FAILED, "Google OAuth2 redirectUri 未配置");
        }
        return redirectUri;
    }
}
