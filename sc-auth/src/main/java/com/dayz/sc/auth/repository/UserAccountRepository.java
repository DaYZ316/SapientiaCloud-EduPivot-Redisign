package com.dayz.sc.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.auth.model.entity.User;
import com.dayz.sc.auth.model.entity.UserIdentity;
import com.dayz.sc.auth.model.enums.OauthProvider;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 系统用户与 OAuth 身份的持久化边界。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public interface UserAccountRepository {
    Optional<UserIdentity> findIdentity(OauthProvider provider, String providerUserId);

    Optional<User> findUser(UUID userId);

    Optional<User> findByEmail(String email);

    User saveUser(User user);

    UserIdentity saveIdentity(UserIdentity identity);

    List<OauthProvider> findLinkedProviders(UUID userId);

    long countUsers(LambdaQueryWrapper<User> wrapper);

    List<User> findUsers(LambdaQueryWrapper<User> wrapper);

    List<User> findUsersByIds(Collection<UUID> ids);

    List<UserIdentity> findIdentities(LambdaQueryWrapper<UserIdentity> wrapper);
}
