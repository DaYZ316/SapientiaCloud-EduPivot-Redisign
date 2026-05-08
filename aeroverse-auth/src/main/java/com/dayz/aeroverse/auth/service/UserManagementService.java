package com.dayz.aeroverse.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.aeroverse.auth.mapper.UserIdentityMapper;
import com.dayz.aeroverse.auth.mapper.UserMapper;
import com.dayz.aeroverse.auth.model.dto.UpdateUserRequest;
import com.dayz.aeroverse.auth.model.dto.UserPageRequest;
import com.dayz.aeroverse.auth.model.entity.User;
import com.dayz.aeroverse.auth.model.entity.UserIdentity;
import com.dayz.aeroverse.auth.model.enums.OauthProvider;
import com.dayz.aeroverse.auth.model.enums.UserStatus;
import com.dayz.aeroverse.auth.model.vo.UserProfile;
import com.dayz.aeroverse.auth.repository.UserAccountRepository;
import com.dayz.aeroverse.common.error.BusinessException;
import com.dayz.aeroverse.common.error.ErrorCodes;
import com.dayz.aeroverse.common.response.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final long DEFAULT_PAGE = 1;
    private static final long DEFAULT_SIZE = 10;
    private static final long MAX_SIZE = 100;

    private final UserMapper userMapper;
    private final UserIdentityMapper userIdentityMapper;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    @Autowired
    public UserManagementService(UserMapper userMapper,
                                 UserIdentityMapper userIdentityMapper,
                                 UserAccountRepository userAccountRepository) {
        this(userMapper, userIdentityMapper, userAccountRepository, Clock.systemUTC());
    }

    UserManagementService(UserMapper userMapper,
                          UserIdentityMapper userIdentityMapper,
                          UserAccountRepository userAccountRepository,
                          Clock clock) {
        this.userMapper = userMapper;
        this.userIdentityMapper = userIdentityMapper;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    public PageResponse<UserProfile> pageUsers(UserPageRequest request) {
        UserPageRequest pageRequest = request == null ? new UserPageRequest(null, null, null, null) : request;
        long currentPage = normalizePage(pageRequest.page());
        long pageSize = normalizeSize(pageRequest.size());
        LambdaQueryWrapper<User> countWrapper = buildQueryWrapper(pageRequest.keyword(), pageRequest.status());
        long total = userMapper.selectCount(countWrapper);
        if (total == 0) {
            return PageResponse.empty(currentPage, pageSize);
        }

        long offset = (currentPage - 1) * pageSize;
        LambdaQueryWrapper<User> listWrapper = buildQueryWrapper(pageRequest.keyword(), pageRequest.status())
                .orderByDesc(User::getUpdatedAt)
                .orderByDesc(User::getCreatedAt)
                .last("LIMIT " + pageSize + " OFFSET " + offset);
        List<User> users = userMapper.selectList(listWrapper);
        Map<UUID, List<OauthProvider>> linkedProviders = loadLinkedProviders(users);
        List<UserProfile> records = users.stream()
                .map(user -> toUserProfile(user, linkedProviders.getOrDefault(user.getId(), List.of())))
                .toList();

        return PageResponse.of(records, total, currentPage, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserProfile updateUser(UUID id, UpdateUserRequest request) {
        if (id == null || request == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST);
        }

        User user = userAccountRepository.findUser(id)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "User not found"));

        applyUpdate(user, request);
        user.setUpdatedAt(Instant.now(clock));
        userAccountRepository.saveUser(user);

        return toUserProfile(user, userAccountRepository.findLinkedProviders(user.getId()));
    }

    private LambdaQueryWrapper<User> buildQueryWrapper(String keyword, UserStatus status) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(User::getStatus, status);
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

        List<UserIdentity> identities = userIdentityMapper.selectList(new LambdaQueryWrapper<UserIdentity>()
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
        if (request.locale() != null) {
            user.setLocale(normalize(request.locale()));
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
    }

    private UserProfile toUserProfile(User user, List<OauthProvider> linkedProviders) {
        return new UserProfile(
                user.getId(),
                user.getEmail(),
                user.isEmailVerified(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getLocale(),
                user.getStatus(),
                user.getCreatedProvider(),
                user.getCreatedIp(),
                user.getLastLoginProvider(),
                user.getLastLoginIp(),
                user.getLoginCount(),
                linkedProviders
        );
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
