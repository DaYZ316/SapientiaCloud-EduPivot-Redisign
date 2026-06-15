package com.dayz.sc.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.auth.model.dto.UpdateUserRequest;
import com.dayz.sc.auth.model.dto.UserBasicInfo;
import com.dayz.sc.auth.model.dto.UserPageRequest;
import com.dayz.sc.auth.model.entity.Student;
import com.dayz.sc.auth.model.entity.Teacher;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import com.dayz.sc.auth.model.vo.StudentInfoVO;
import com.dayz.sc.auth.model.vo.TeacherInfoVO;
import com.dayz.sc.auth.model.vo.UserProfileVO;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.auth.repository.StudentRepository;
import com.dayz.sc.auth.repository.TeacherRepository;
import com.dayz.sc.auth.repository.UserAccountRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.model.UserRole;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.auth.event.UserEventPublisher;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 处理系统用户分页查询、资料更新与关联登录来源聚合。
 *
 * @author DaYZ
 * @since 2026-05-08
 */
@Service
public class UserManagementService {

    private static final String DEFAULT_PASSWORD = "SapientiaCloud123";

    private final UserAccountRepository userAccountRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageInternalClient storageInternalClient;
    private final UserEventPublisher userEventPublisher;
    private final Clock clock;

    @Autowired
    public UserManagementService(UserAccountRepository userAccountRepository,
                                 StudentRepository studentRepository,
                                 TeacherRepository teacherRepository,
                                 PasswordEncoder passwordEncoder,
                                 StorageInternalClient storageInternalClient,
                                 UserEventPublisher userEventPublisher) {
        this(userAccountRepository, studentRepository, teacherRepository,
                passwordEncoder, storageInternalClient, userEventPublisher, Clock.systemUTC());
    }

    UserManagementService(UserAccountRepository userAccountRepository,
                          StudentRepository studentRepository,
                          TeacherRepository teacherRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock) {
        this(userAccountRepository, studentRepository, teacherRepository,
                passwordEncoder, null, null, clock);
    }

    UserManagementService(UserAccountRepository userAccountRepository,
                          StudentRepository studentRepository,
                          TeacherRepository teacherRepository,
                          PasswordEncoder passwordEncoder,
                          StorageInternalClient storageInternalClient,
                          UserEventPublisher userEventPublisher,
                          Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageInternalClient = storageInternalClient;
        this.userEventPublisher = userEventPublisher;
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
     * 批量获取用户基本信息（displayName, avatarUrl），用于服务间通信。
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

    public UserProfileVO getUser(UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));

        return toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()),
                loadStudentInfo(user.getId()), loadTeacherInfo(user.getId()));
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

        return toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()),
                loadStudentInfo(user.getId()), loadTeacherInfo(user.getId()));
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

        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setUpdatedAt(Instant.now(clock));
        userAccountRepository.saveUser(user);
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
                linkedProviders,
                user.getRole(),
                studentInfo,
                teacherInfo
        );
    }

    private void validateAvatarFile(UUID fileId, UUID userId) {
        StorageObjectInfo file = internalFile(fileId);
        if (!"READY".equals(file.status())
                || !"USER_AVATAR".equals(file.usage())
                || !"USER".equals(file.scopeType())
                || !userId.equals(file.scopeId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid avatar file");
        }
    }

    private StorageObjectInfo internalFile(UUID fileId) {
        if (storageInternalClient == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Storage service is unavailable");
        }
        ApiResponse<StorageObjectInfo> response = storageInternalClient.getFile(fileId);
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
            ApiResponse<Map<UUID, String>> response = storageInternalClient.getUrls(fileIds);
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
}
