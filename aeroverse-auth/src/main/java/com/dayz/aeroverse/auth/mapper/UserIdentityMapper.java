package com.dayz.aeroverse.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.aeroverse.auth.model.entity.UserIdentity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth 第三方身份 MyBatis-Plus Mapper。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
}
