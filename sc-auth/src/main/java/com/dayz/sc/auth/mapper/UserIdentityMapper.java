package com.dayz.sc.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.auth.model.entity.UserIdentity;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth 第三方身份 MyBatis-Plus Mapper
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
}
