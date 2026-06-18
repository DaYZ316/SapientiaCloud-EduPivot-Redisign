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
 * 系统用户与 OAuth 身份的持久化边界
 *
 * @author DaYZ
 * @since 2026-05-07
 */
public interface UserAccountRepository {

    /**
     * 根据OAuth提供商和提供商用户ID查询用户身份
     *
     * @param provider       OAuth提供商
     * @param providerUserId 提供商用户ID
     * @return 用户身份实体，可能为空
     */
    Optional<UserIdentity> findIdentity(OauthProvider provider, String providerUserId);

    /**
     * 根据用户ID查询用户
     *
     * @param userId 用户ID
     * @return 用户实体，可能为空
     */
    Optional<User> findUser(UUID userId);

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户实体，可能为空
     */
    Optional<User> findByEmail(String email);

    /**
     * 保存用户
     *
     * @param user 用户实体
     * @return 保存后的用户实体
     */
    User saveUser(User user);

    /**
     * 保存用户身份
     *
     * @param identity 用户身份实体
     * @return 保存后的用户身份实体
     */
    UserIdentity saveIdentity(UserIdentity identity);

    /**
     * 查询用户已关联的OAuth提供商列表
     *
     * @param userId 用户ID
     * @return OAuth提供商列表
     */
    List<OauthProvider> findLinkedProviders(UUID userId);

    /**
     * 统计用户数量
     *
     * @param wrapper 查询条件
     * @return 用户数量
     */
    long countUsers(LambdaQueryWrapper<User> wrapper);

    /**
     * 根据条件查询用户列表
     *
     * @param wrapper 查询条件
     * @return 用户列表
     */
    List<User> findUsers(LambdaQueryWrapper<User> wrapper);

    /**
     * 根据ID列表批量查询用户
     *
     * @param ids 用户ID列表
     * @return 用户列表
     */
    List<User> findUsersByIds(Collection<UUID> ids);

    /**
     * 根据条件查询用户身份列表
     *
     * @param wrapper 查询条件
     * @return 用户身份列表
     */
    List<UserIdentity> findIdentities(LambdaQueryWrapper<UserIdentity> wrapper);
}
