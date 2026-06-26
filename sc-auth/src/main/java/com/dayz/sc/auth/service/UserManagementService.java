package com.dayz.sc.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.auth.event.UserEventPublisher;
import com.dayz.sc.auth.model.dto.ChangePasswordRequest;
import com.dayz.sc.auth.model.dto.CompleteOnboardingRequest;
import com.dayz.sc.auth.model.dto.UpdateUserRequest;
import com.dayz.sc.auth.model.dto.UserBasicInfo;
import com.dayz.sc.auth.model.dto.UserPageRequest;
import com.dayz.sc.auth.model.entity.Student;
import com.dayz.sc.auth.model.entity.Teacher;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
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
import com.dayz.sc.common.dashboard.DashboardChartPoint;
import com.dayz.sc.common.dashboard.DashboardUserActivity;
import com.dayz.sc.common.dashboard.DashboardUserSummary;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.InternalUserProfile;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.model.UserRole;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.service.JwtTokenService;
import com.dayz.sc.common.security.token.RefreshTokenService;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 澶勭悊绯荤粺鐢ㄦ埛鍒嗛〉鏌ヨ銆佽祫鏂欐洿鏂颁笌鍏宠仈鐧诲綍鏉ユ簮鑱氬悎
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Service
public class UserManagementService {

    private static final String DEFAULT_PASSWORD = "SapientiaCloud123";
    private static final String TOKEN_TYPE = "Bearer";
    private static final String STATUS_READY = "READY";
    private static final String USAGE_USER_AVATAR = "USER_AVATAR";
    private static final String SCOPE_TYPE_USER = "USER";

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageInternalClient storageInternalClient;
    private final UserEventPublisher userEventPublisher;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    @Autowired
    public UserManagementService(UserAccountRepository userAccountRepository,
                                 StudentRepository studentRepository,
                                 TeacherRepository teacherRepository,
                                 PasswordEncoder passwordEncoder,
                                 StorageInternalClient storageInternalClient,
                                 UserEventPublisher userEventPublisher,
                                 JwtTokenService jwtTokenService,
                                 RefreshTokenService refreshTokenService) {
        this(userAccountRepository, studentRepository, teacherRepository,
                passwordEncoder, storageInternalClient, userEventPublisher,
                jwtTokenService, refreshTokenService, Clock.systemUTC());
    }

    UserManagementService(UserAccountRepository userAccountRepository,
                          StudentRepository studentRepository,
                          TeacherRepository teacherRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock) {
        this(userAccountRepository, studentRepository, teacherRepository,
                passwordEncoder, null, null, null, null, clock);
    }

    UserManagementService(UserAccountRepository userAccountRepository,
                          StudentRepository studentRepository,
                          TeacherRepository teacherRepository,
                          PasswordEncoder passwordEncoder,
                          StorageInternalClient storageInternalClient,
                          UserEventPublisher userEventPublisher,
                          JwtTokenService jwtTokenService,
                          RefreshTokenService refreshTokenService,
                          Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageInternalClient = storageInternalClient;
        this.userEventPublisher = userEventPublisher;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    public PageResponse<@NonNull UserProfileVO> pageUsers(UserPageRequest request) {
        UserPageRequest pageRequest = request == null ? new UserPageRequest(null, null, null, null, null) : request;
        long currentPage = PageUtils.normalizePage(pageRequest.page());
        long pageSize = PageUtils.normalizeSize(pageRequest.size());
        LambdaQueryWrapper<User> countWrapper = buildQueryWrapper(pageRequest.keyword(), pageRequest.status(), pageRequest.role());
        long total = userAccountRepository.countUsers(countWrapper);
        if (total == 0) {
            return PageResponse.empty(currentPage, pageSize);
        }

        long offset = (currentPage - 1) * pageSize;
        LambdaQueryWrapper<User> listWrapper = buildQueryWrapper(pageRequest.keyword(), pageRequest.status(), pageRequest.role())
                .last("ORDER BY CASE WHEN role = " + UserRole.ADMIN.getCode() + " THEN 0 ELSE 1 END, updated_at DESC, created_at DESC LIMIT " + pageSize + " OFFSET " + offset);
        List<User> users = userAccountRepository.findUsers(listWrapper);
        List<UUID> userIds = users.stream().map(User::getId).toList();

        Map<UUID, List<OauthProvider>> linkedProviders = loadLinkedProviders(users);
        Map<UUID, StudentInfoVO> studentInfoMap = loadStudentInfoMap(userIds);
        Map<UUID, TeacherInfoVO> teacherInfoMap = loadTeacherInfoMap(userIds);
        Map<UUID, String> avatarUrls = loadAvatarUrls(users);

        List<UserProfileVO> records = users.stream()
                .map(user -> toUserProfile(user,
                        linkedProviders.getOrDefault(user.getId(), List.of()),
                        studentInfoMap.get(user.getId()),
                        teacherInfoMap.get(user.getId()),
                        resolveAvatarUrl(user, avatarUrls)))
                .toList();

        return PageResponse.of(records, total, currentPage, pageSize);
    }

    /**
     * 鎵归噺鑾峰彇鐢ㄦ埛鍩烘湰淇℃伅锛坉isplayName, avatarUrl锛夛紝鐢ㄤ簬鏈嶅姟闂撮€氫俊
     */
    public List<UserBasicInfo> getUsersBasicInfo(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<User> users = userAccountRepository.findUsersByIds(ids);
        Map<UUID, String> avatarUrls = loadAvatarUrls(users);

        return users.stream()
                .map(user -> new UserBasicInfo(
                        user.getId(),
                        user.getDisplayName(),
                        resolveAvatarUrl(user, avatarUrls),
                        user.getRole()))
                .toList();
    }

    public InternalUserProfile getInternalUserProfile(UUID id) {
        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));
        Map<UUID, String> avatarUrls = loadAvatarUrls(List.of(user));
        return new InternalUserProfile(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                resolveAvatarUrl(user, avatarUrls),
                user.getRole());
    }

    public DashboardUserSummary getDashboardSummary() {
        Instant todayStart = Instant.now(clock).atZone(clock.getZone()).toLocalDate()
                .atStartOfDay(clock.getZone())
                .toInstant();
        long total = countUsers(null, null);
        long students = countUsers(null, UserRole.STUDENT.getCode());
        long teachers = countUsers(null, UserRole.TEACHER.getCode());
        long admins = countUsers(null, UserRole.ADMIN.getCode());
        long disabled = countUsers(UserStatus.DISABLED, null);
        long todayUsers = userAccountRepository.countUsers(new LambdaQueryWrapper<User>()
                .ge(User::getCreatedAt, todayStart));
        long incompleteProfiles = userAccountRepository.countUsers(new LambdaQueryWrapper<User>()
                .and(wrapper -> wrapper
                        .isNull(User::getDisplayName)
                        .or()
                        .eq(User::getDisplayName, "")
                        .or()
                        .eq(User::getEmailVerified, false)
                        .or()
                        .isNull(User::getEmailVerified)));
        long oauthUsers = userAccountRepository.countUsers(new LambdaQueryWrapper<User>()
                .ne(User::getCreatedProvider, OauthProvider.LOCAL));
        int oauthPercent = total == 0 ? 0 : (int) Math.round(oauthUsers * 100.0 / total);
        List<User> recentUsers = userAccountRepository.findUsers(new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt)
                .last("LIMIT 8"));
        return new DashboardUserSummary(
                total,
                students,
                teachers,
                admins,
                disabled,
                todayUsers,
                incompleteProfiles,
                oauthPercent,
                List.of(
                        new DashboardChartPoint("瀛︾敓", students),
                        new DashboardChartPoint("鏁欏笀", teachers),
                        new DashboardChartPoint("绠＄悊鍛?鍏朵粬", Math.max(0, total - students - teachers))
                ),
                recentUsers.stream()
                        .map(user -> new DashboardUserActivity(
                                user.getId(),
                                user.getDisplayName(),
                                user.getEmail(),
                                user.getCreatedAt(),
                                user.getLastLoginAt()))
                        .toList());
    }

    public UserProfileVO getUser(UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));

        return toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()),
                loadStudentInfo(user.getId()), loadTeacherInfo(user.getId()));
    }

    public boolean isProfileComplete(UUID id) {
        if (id == null) {
            return false;
        }
        return userAccountRepository.findUser(id)
                .map(user -> user.getRole() != null && StringUtils.hasText(user.getDisplayName()))
                .orElse(false);
    }

    public AuthTokenState getAuthTokenState(UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED);
        }
        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED));
        return new AuthTokenState(user.getRole(), user.getRole() != null && StringUtils.hasText(user.getDisplayName()));
    }

    private long countUsers(UserStatus status, Integer role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        return userAccountRepository.countUsers(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateCurrentUser(UUID id, UpdateUserRequest request) {
        if (id == null || request == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));

        applyCurrentUserUpdate(user, request);
        user.setUpdatedAt(Instant.now(clock));
        userAccountRepository.saveUser(user);
        updateRoleProfile(user, request);

        return toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()),
                loadStudentInfo(user.getId()), loadTeacherInfo(user.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO changeCurrentUserPassword(UUID id, ChangePasswordRequest request) {
        if (id == null || request == null || !isPasswordLengthValid(request.newPassword())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Password must be 8-64 characters");
        }

        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));

        List<OauthProvider> linkedProviders = userAccountRepository.findLinkedProviders(user.getId());
        if (StringUtils.hasText(user.getPasswordHash())) {
            if (!StringUtils.hasText(request.currentPassword())
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Current password is incorrect");
            }
        } else if (!canSetInitialPassword(user, linkedProviders)) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Verified OAuth account is required to set a password");
        }

        Instant now = Instant.now(clock);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(now);
        userAccountRepository.saveUser(user);
        ensureLocalIdentity(user, now);

        return toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()),
                loadStudentInfo(user.getId()), loadTeacherInfo(user.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginResponseVO completeOnboarding(UUID id, CompleteOnboardingRequest request) {
        if (id == null || request == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));
        UserRole requestedRole = UserRole.fromCode(request.role());
        if (requestedRole != UserRole.STUDENT && requestedRole != UserRole.TEACHER) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Role must be student or teacher");
        }
        if (user.getRole() != null && !Objects.equals(user.getRole(), request.role())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Role has already been selected");
        }
        if (user.getRole() != null && StringUtils.hasText(user.getDisplayName())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Onboarding already completed");
        }

        user.setRole(request.role());
        user.setDisplayName(normalizeRequiredDisplayName(request.displayName()));
        user.setUpdatedAt(Instant.now(clock));
        userAccountRepository.saveUser(user);
        ensureRoleProfile(user.getId(), requestedRole);

        UserProfileVO profile = toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()),
                loadStudentInfo(user.getId()), loadTeacherInfo(user.getId()));
        String accessToken = jwtTokenService.createAccessToken(user.getId().toString(), buildClaims(user));
        String refreshToken = refreshTokenService.createRefreshToken(user.getId().toString(), user.getRole());
        return new LoginResponseVO(accessToken, refreshToken, TOKEN_TYPE, jwtTokenService.getAccessTokenTtlSeconds(), profile);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateUser(UUID id, UpdateUserRequest request) {
        if (id == null || request == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));

        boolean wasActive = user.getStatus() == UserStatus.ACTIVE;
        applyUpdate(user, request);
        user.setUpdatedAt(Instant.now(clock));
        userAccountRepository.saveUser(user);
        updateRoleProfile(user, request);

        if (wasActive && user.getStatus() == UserStatus.DISABLED && userEventPublisher != null) {
            userEventPublisher.publishUserDeactivated(user);
        }

        return toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()),
                loadStudentInfo(user.getId()), loadTeacherInfo(user.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        User user = userAccountRepository.findUser(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));

        Instant now = Instant.now(clock);
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setUpdatedAt(now);
        userAccountRepository.saveUser(user);
        ensureLocalIdentity(user, now);
    }

    private boolean isPasswordLengthValid(String password) {
        return StringUtils.hasText(password) && password.length() >= 8 && password.length() <= 64;
    }

    private boolean canSetInitialPassword(User user, List<OauthProvider> linkedProviders) {
        return Boolean.TRUE.equals(user.getEmailVerified())
                && linkedProviders.stream().anyMatch(provider -> provider != OauthProvider.LOCAL);
    }

    private void ensureLocalIdentity(User user, Instant now) {
        if (userAccountRepository.findLinkedProviders(user.getId()).contains(OauthProvider.LOCAL)) {
            return;
        }

        UserIdentity identity = new UserIdentity();
        identity.setId(UuidV7Generator.generate());
        identity.setUserId(user.getId());
        identity.setProvider(OauthProvider.LOCAL);
        identity.setProviderUserId(user.getId().toString());
        identity.setProviderLogin(user.getEmail());
        identity.setProviderEmail(user.getEmail());
        identity.setProviderEmailVerified(user.getEmailVerified());
        identity.setProviderDisplayName(user.getDisplayName());
        identity.setProviderAvatarUrl(user.getAvatarUrl());
        identity.setLinkedAt(now);
        userAccountRepository.saveIdentity(identity);
    }

    private void ensureRoleProfile(UUID userId, UserRole role) {
        Instant now = Instant.now(clock);
        if (role == UserRole.STUDENT && studentRepository.findByUserId(userId).isEmpty()) {
            Student student = new Student();
            student.setId(UuidV7Generator.generate());
            student.setUserId(userId);
            student.setStudentNo(generateProfileNo("S"));
            student.setCreatedAt(now);
            student.setUpdatedAt(now);
            studentRepository.save(student);
        } else if (role == UserRole.TEACHER && teacherRepository.findByUserId(userId).isEmpty()) {
            Teacher teacher = new Teacher();
            teacher.setId(UuidV7Generator.generate());
            teacher.setUserId(userId);
            teacher.setEmployeeNo(generateProfileNo("T"));
            teacher.setCreatedAt(now);
            teacher.setUpdatedAt(now);
            teacherRepository.save(teacher);
        }
    }

    private String generateProfileNo(String prefix) {
        return prefix + Instant.now(clock).toEpochMilli() + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    private String normalizeRequiredDisplayName(String value) {
        String normalized = value == null ? null : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Display name is required");
        }
        return normalized;
    }

    private Map<String, Object> buildClaims(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("profileComplete", user.getRole() != null && StringUtils.hasText(user.getDisplayName()));
        if (user.getRole() != null) {
            claims.put("role", user.getRole());
        }
        return claims;
    }

    private LambdaQueryWrapper<User> buildQueryWrapper(String keyword, UserStatus status, Integer role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query
                    .like(User::getEmail, value)
                    .or()
                    .like(User::getDisplayName, value));
        }
        return wrapper;
    }

    private Map<UUID, List<OauthProvider>> loadLinkedProviders(List<User> users) {
        List<UUID> userIds = users.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<UserIdentity> identities = userAccountRepository.findIdentities(new LambdaQueryWrapper<UserIdentity>()
                .select(UserIdentity::getUserId, UserIdentity::getProvider)
                .in(UserIdentity::getUserId, userIds));

        return identities.stream()
                .collect(Collectors.groupingBy(
                        UserIdentity::getUserId,
                        LinkedHashMap::new,
                        Collectors.mapping(UserIdentity::getProvider, Collectors.toList())
                ));
    }

    private void applyUpdate(User user, UpdateUserRequest request) {
        if (request.email() != null) {
            String email = normalize(request.email());
            if (!Objects.equals(user.getEmail(), email)) {
                user.setEmail(email);
                if (request.emailVerified() == null) {
                    user.setEmailVerified(false);
                }
            }
        }
        if (request.emailVerified() != null) {
            user.setEmailVerified(request.emailVerified());
        }
        if (request.displayName() != null) {
            user.setDisplayName(normalize(request.displayName()));
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalize(request.avatarUrl()));
        }
        if (request.avatarFileId() != null) {
            validateAvatarFile(request.avatarFileId(), user.getId());
            user.setAvatarFileId(request.avatarFileId());
        }
        if (request.locale() != null) {
            user.setLocale(normalize(request.locale()));
        }
        if (request.phone() != null) {
            user.setPhone(normalize(request.phone()));
        }
        if (request.bio() != null) {
            user.setBio(normalize(request.bio()));
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.birthday() != null) {
            user.setBirthday(request.birthday());
        }
        if (request.theme() != null) {
            user.setTheme(request.theme());
        }
        if (request.notificationEnabled() != null) {
            user.setNotificationEnabled(request.notificationEnabled());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
    }

    private void applyCurrentUserUpdate(User user, UpdateUserRequest request) {
        if (request.displayName() != null) {
            user.setDisplayName(normalize(request.displayName()));
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(normalize(request.avatarUrl()));
        }
        if (request.avatarFileId() != null) {
            validateAvatarFile(request.avatarFileId(), user.getId());
            user.setAvatarFileId(request.avatarFileId());
        }
        if (request.locale() != null) {
            user.setLocale(normalize(request.locale()));
        }
        if (request.phone() != null) {
            user.setPhone(normalize(request.phone()));
        }
        if (request.bio() != null) {
            user.setBio(normalize(request.bio()));
        }
        if (request.gender() != null) {
            user.setGender(request.gender());
        }
        if (request.birthday() != null) {
            user.setBirthday(request.birthday());
        }
        if (request.theme() != null) {
            user.setTheme(request.theme());
        }
        if (request.notificationEnabled() != null) {
            user.setNotificationEnabled(request.notificationEnabled());
        }
    }

    private void updateRoleProfile(User user, UpdateUserRequest request) {
        UserRole role = UserRole.fromCode(user.getRole());
        if (role == UserRole.STUDENT && request.studentInfo() != null) {
            updateStudentProfile(user.getId(), request.studentInfo());
            return;
        }
        if (role == UserRole.TEACHER && request.teacherInfo() != null) {
            updateTeacherProfile(user.getId(), request.teacherInfo());
        }
    }

    private void updateStudentProfile(UUID userId, UpdateUserRequest.StudentInfoUpdate request) {
        Student student = studentRepository.findByUserId(userId)
                .orElseGet(() -> newStudentProfile(userId));
        if (request.grade() != null) {
            student.setGrade(normalize(request.grade()));
        }
        if (request.major() != null) {
            student.setMajor(normalize(request.major()));
        }
        if (request.school() != null) {
            student.setSchool(normalize(request.school()));
        }
        student.setUpdatedAt(Instant.now(clock));
        if (student.getCreatedAt() == null) {
            student.setCreatedAt(student.getUpdatedAt());
            studentRepository.save(student);
            return;
        }
        studentRepository.update(student);
    }

    private void updateTeacherProfile(UUID userId, UpdateUserRequest.TeacherInfoUpdate request) {
        Teacher teacher = teacherRepository.findByUserId(userId)
                .orElseGet(() -> newTeacherProfile(userId));
        if (request.department() != null) {
            teacher.setDepartment(normalize(request.department()));
        }
        if (request.title() != null) {
            teacher.setTitle(normalize(request.title()));
        }
        if (request.school() != null) {
            teacher.setSchool(normalize(request.school()));
        }
        teacher.setUpdatedAt(Instant.now(clock));
        if (teacher.getCreatedAt() == null) {
            teacher.setCreatedAt(teacher.getUpdatedAt());
            teacherRepository.save(teacher);
            return;
        }
        teacherRepository.update(teacher);
    }

    private Student newStudentProfile(UUID userId) {
        Student student = new Student();
        student.setId(UuidV7Generator.generate());
        student.setUserId(userId);
        student.setStudentNo(generateProfileNo("S"));
        return student;
    }

    private Teacher newTeacherProfile(UUID userId) {
        Teacher teacher = new Teacher();
        teacher.setId(UuidV7Generator.generate());
        teacher.setUserId(userId);
        teacher.setEmployeeNo(generateProfileNo("T"));
        return teacher;
    }

    private UserProfileVO toUserProfile(User user, List<OauthProvider> linkedProviders,
                                        StudentInfoVO studentInfo, TeacherInfoVO teacherInfo) {
        return toUserProfile(user, linkedProviders, studentInfo, teacherInfo, resolveAvatarUrl(user));
    }

    private UserProfileVO toUserProfile(User user, List<OauthProvider> linkedProviders,
                                        StudentInfoVO studentInfo, TeacherInfoVO teacherInfo, String avatarUrl) {
        return new UserProfileVO(
                user.getId(),
                user.getEmail(),
                user.getEmailVerified(),
                user.getDisplayName(),
                avatarUrl,
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
                linkedProvidersFor(user, linkedProviders),
                user.getRole(),
                studentInfo,
                teacherInfo
        );
    }

    private List<OauthProvider> linkedProvidersFor(User user, List<OauthProvider> linkedProviders) {
        List<OauthProvider> providers = linkedProviders == null ? List.of() : linkedProviders;
        if (!StringUtils.hasText(user.getPasswordHash()) || providers.contains(OauthProvider.LOCAL)) {
            return providers;
        }
        List<OauthProvider> mergedProviders = new ArrayList<>(providers);
        mergedProviders.add(OauthProvider.LOCAL);
        return mergedProviders;
    }

    private void validateAvatarFile(UUID fileId, UUID userId) {
        StorageObjectInfo file = internalFile(fileId);
        if (!STATUS_READY.equals(file.status())
                || !USAGE_USER_AVATAR.equals(file.usage())
                || !SCOPE_TYPE_USER.equals(file.scopeType())
                || !userId.equals(file.scopeId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid avatar file");
        }
    }

    private StorageObjectInfo internalFile(UUID fileId) {
        if (storageInternalClient == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Storage service is unavailable");
        }
        ApiResponse<@NonNull StorageObjectInfo> response = storageInternalClient.getFile(fileId);
        if (response == null || response.code() != ErrorCodes.SUCCESS.code() || response.data() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid storage file");
        }
        return response.data();
    }

    private String resolveAvatarUrl(User user) {
        if (user.getAvatarFileId() == null) {
            return user.getAvatarUrl();
        }
        return loadAvatarUrls(List.of(user)).getOrDefault(user.getAvatarFileId(), user.getAvatarUrl());
    }

    private String resolveAvatarUrl(User user, Map<UUID, String> avatarUrls) {
        if (user.getAvatarFileId() == null) {
            return user.getAvatarUrl();
        }
        return avatarUrls.getOrDefault(user.getAvatarFileId(), user.getAvatarUrl());
    }

    private Map<UUID, String> loadAvatarUrls(List<User> users) {
        if (storageInternalClient == null) {
            return Map.of();
        }
        List<UUID> fileIds = users.stream()
                .map(User::getAvatarFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        try {
            ApiResponse<@NonNull Map<@NonNull UUID, @NonNull String>> response = storageInternalClient.getUrls(fileIds);
            if (response != null && response.code() == ErrorCodes.SUCCESS.code() && response.data() != null) {
                return response.data();
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
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

    private Map<UUID, StudentInfoVO> loadStudentInfoMap(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return studentRepository.findByUserIds(userIds).stream()
                .collect(Collectors.toMap(Student::getUserId, s ->
                        new StudentInfoVO(s.getId(), s.getStudentNo(), s.getGrade(), s.getMajor(), s.getSchool())));
    }

    private Map<UUID, TeacherInfoVO> loadTeacherInfoMap(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return teacherRepository.findByUserIds(userIds).stream()
                .collect(Collectors.toMap(Teacher::getUserId, t ->
                        new TeacherInfoVO(t.getId(), t.getEmployeeNo(), t.getDepartment(), t.getTitle(), t.getSchool())));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record AuthTokenState(Integer role, boolean profileComplete) {
    }
}
