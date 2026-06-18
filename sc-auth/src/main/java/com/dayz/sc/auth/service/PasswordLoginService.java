package com.dayz.sc.auth.service;

import com.dayz.sc.auth.event.UserEventPublisher;
import com.dayz.sc.auth.model.dto.PasswordLoginRequest;
import com.dayz.sc.auth.model.dto.RegisterRequest;
import com.dayz.sc.auth.model.entity.Student;
import com.dayz.sc.auth.model.entity.Teacher;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import com.dayz.sc.auth.model.vo.LoginResponseVO;
import com.dayz.sc.auth.model.vo.StudentInfoVO;
import com.dayz.sc.auth.model.vo.TeacherInfoVO;
import com.dayz.sc.auth.model.vo.UserProfileVO;
import com.dayz.sc.auth.repository.StudentRepository;
import com.dayz.sc.auth.repository.TeacherRepository;
import com.dayz.sc.auth.repository.UserAccountRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.model.UserRole;
import com.dayz.sc.common.security.service.JwtTokenService;
import com.dayz.sc.common.security.token.RefreshTokenService;
import com.dayz.sc.common.util.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 账号密码登录与注册服务
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Service
@RequiredArgsConstructor
public class PasswordLoginService {
    private static final String TOKEN_TYPE = "Bearer";

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserEventPublisher userEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public LoginResponseVO register(RegisterRequest request, String clientIp) {
        userAccountRepository.findByEmail(request.email())
                .ifPresent(user -> {
                    throw new BusinessException(ErrorCodes.EMAIL_ALREADY_EXISTS);
                });

        Instant now = Instant.now();
        UUID userId = UuidV7Generator.generate();
        String normalizedIp = normalize(clientIp);

        User user = new User();
        user.setId(userId);
        user.setEmail(request.email());
        user.setEmailVerified(false);
        user.setDisplayName(StringUtils.hasText(request.displayName())
                ? request.displayName()
                : request.email().substring(0, request.email().indexOf('@')));
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);
        user.setCreatedProvider(OauthProvider.LOCAL);
        user.setCreatedIp(normalizedIp);
        user.setLastLoginProvider(OauthProvider.LOCAL);
        user.setLastLoginIp(normalizedIp);
        user.setLoginCount(1L);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        int role = request.role() != null ? request.role() : UserRole.STUDENT.getCode();
        user.setRole(role);

        userAccountRepository.saveUser(user);

        // 根据角色创建对应的扩展记录
        Instant now2 = Instant.now();
        if (UserRole.fromCode(role) == UserRole.STUDENT) {
            Student student = new Student();
            student.setId(UuidV7Generator.generate());
            student.setUserId(userId);
            student.setStudentNo("S" + System.currentTimeMillis());
            student.setCreatedAt(now2);
            student.setUpdatedAt(now2);
            studentRepository.save(student);
        } else if (UserRole.fromCode(role) == UserRole.TEACHER) {
            Teacher teacher = new Teacher();
            teacher.setId(UuidV7Generator.generate());
            teacher.setUserId(userId);
            teacher.setEmployeeNo("T" + System.currentTimeMillis());
            teacher.setCreatedAt(now2);
            teacher.setUpdatedAt(now2);
            teacherRepository.save(teacher);
        }

        userEventPublisher.publishUserRegistered(user);

        String accessToken = jwtTokenService.createAccessToken(userId.toString(), buildClaims(user));
        String refreshToken = refreshTokenService.createRefreshToken(userId.toString(), user.getRole());
        UserProfileVO profile = toUserProfile(user, List.of(OauthProvider.LOCAL));

        return new LoginResponseVO(accessToken, refreshToken, TOKEN_TYPE, jwtTokenService.getAccessTokenTtlSeconds(), profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponseVO login(PasswordLoginRequest request, String clientIp) {
        User user = userAccountRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCodes.PASSWORD_LOGIN_FAILED));

        if (!StringUtils.hasText(user.getPasswordHash())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCodes.PASSWORD_LOGIN_FAILED);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCodes.PASSWORD_LOGIN_FAILED, "账号已被禁用");
        }

        Instant now = Instant.now();
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);
        user.setLastLoginProvider(OauthProvider.LOCAL);
        user.setLastLoginIp(normalize(clientIp));
        user.setLoginCount(user.getLoginCount() + 1);
        userAccountRepository.saveUser(user);

        List<OauthProvider> linkedProviders = userAccountRepository.findLinkedProviders(user.getId());
        String accessToken = jwtTokenService.createAccessToken(user.getId().toString(), buildClaims(user));
        String refreshToken = refreshTokenService.createRefreshToken(user.getId().toString(), user.getRole());
        UserProfileVO profile = toUserProfile(user, linkedProviders);

        return new LoginResponseVO(accessToken, refreshToken, TOKEN_TYPE, jwtTokenService.getAccessTokenTtlSeconds(), profile);
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

    private UserProfileVO toUserProfile(User user, List<OauthProvider> linkedProviders) {
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
                linkedProviders,
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

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
