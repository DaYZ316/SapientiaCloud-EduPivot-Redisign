package com.dayz.sc.auth.service;

import com.dayz.sc.auth.model.dto.OauthUserInfo;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;
import com.dayz.sc.auth.model.enums.UserStatus;
import com.dayz.sc.auth.repository.UserAccountRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.util.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 协调内部系统用户与外部 OAuth 身份
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Service
public class UserAccountService {
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    @Autowired
    public UserAccountService(UserAccountRepository userAccountRepository) {
        this(userAccountRepository, Clock.systemUTC());
    }

    UserAccountService(UserAccountRepository userAccountRepository, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticatedUser loginWithOauth(OauthUserInfo userInfo, String clientIp) {
        if (userInfo == null
                || userInfo.provider() == null
                || !StringUtils.hasText(userInfo.providerUserId())) {
            throw new BusinessException(ErrorCodes.UNAUTHORIZED, "OAuth 用户身份缺失");
        }

        return userAccountRepository
                .findIdentity(userInfo.provider(), userInfo.providerUserId())
                .map(identity -> loginExistingUser(identity, userInfo, clientIp))
                .orElseGet(() -> createUserWithIdentity(userInfo, clientIp));
    }

    private AuthenticatedUser loginExistingUser(UserIdentity identity, OauthUserInfo userInfo, String clientIp) {
        Instant now = Instant.now(clock);
        User user = userAccountRepository.findUser(identity.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.UNAUTHORIZED, "用户身份未绑定系统账户"));

        updateUserFromOauth(user, userInfo, now, clientIp);
        updateIdentityFromOauth(identity, userInfo, now);
        userAccountRepository.saveUser(user);
        userAccountRepository.saveIdentity(identity);

        return toAuthenticatedUser(user);
    }

    private AuthenticatedUser createUserWithIdentity(OauthUserInfo userInfo, String clientIp) {
        Instant now = Instant.now(clock);
        UUID userId = UuidV7Generator.generate();
        String normalizedClientIp = normalize(clientIp);
        User user = new User(
                userId,
                normalize(userInfo.email()),
                userInfo.emailVerified(),
                normalize(userInfo.displayName()),
                normalize(userInfo.avatarUrl()),
                null,
                normalize(userInfo.locale()),
                UserStatus.ACTIVE,
                now,
                now,
                now,
                userInfo.provider(),
                normalizedClientIp,
                userInfo.provider(),
                normalizedClientIp,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                "system",
                true
        );
        UserIdentity identity = new UserIdentity(
                UuidV7Generator.generate(),
                userId,
                userInfo.provider(),
                userInfo.providerUserId(),
                normalize(userInfo.providerLogin()),
                normalize(userInfo.email()),
                userInfo.emailVerified(),
                normalize(userInfo.displayName()),
                normalize(userInfo.avatarUrl()),
                now,
                now
        );

        userAccountRepository.saveUser(user);
        userAccountRepository.saveIdentity(identity);

        return toAuthenticatedUser(user);
    }

    private void updateUserFromOauth(User user, OauthUserInfo userInfo, Instant now, String clientIp) {
        if (!StringUtils.hasText(user.getEmail()) && StringUtils.hasText(userInfo.email())) {
            user.setEmail(userInfo.email());
            user.setEmailVerified(userInfo.emailVerified());
        } else if (userInfo.emailVerified()
                && StringUtils.hasText(userInfo.email())
                && userInfo.email().equals(user.getEmail())) {
            user.setEmailVerified(true);
        }
        if (StringUtils.hasText(userInfo.displayName())) {
            user.setDisplayName(userInfo.displayName());
        }
        if (StringUtils.hasText(userInfo.avatarUrl())) {
            user.setAvatarUrl(userInfo.avatarUrl());
        }
        if (StringUtils.hasText(userInfo.locale())) {
            user.setLocale(userInfo.locale());
        }
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);
        user.setLastLoginProvider(userInfo.provider());
        user.setLastLoginIp(normalize(clientIp));
        user.setLoginCount(user.getLoginCount() + 1);
    }

    private void updateIdentityFromOauth(UserIdentity identity, OauthUserInfo userInfo, Instant now) {
        identity.setProviderLogin(normalize(userInfo.providerLogin()));
        identity.setProviderEmail(normalize(userInfo.email()));
        identity.setProviderEmailVerified(userInfo.emailVerified());
        identity.setProviderDisplayName(normalize(userInfo.displayName()));
        identity.setProviderAvatarUrl(normalize(userInfo.avatarUrl()));
        identity.setLastLoginAt(now);
    }

    private AuthenticatedUser toAuthenticatedUser(User user) {
        List<OauthProvider> linkedProviders = userAccountRepository.findLinkedProviders(user.getId());
        return new AuthenticatedUser(user, linkedProviders);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    public record AuthenticatedUser(User user, List<OauthProvider> linkedProviders) {
    }
}
