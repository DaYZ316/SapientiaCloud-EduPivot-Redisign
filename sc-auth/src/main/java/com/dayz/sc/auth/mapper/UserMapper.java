package com.dayz.sc.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.auth.model.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 MyBatis-Plus Mapper。
 *
 * @author DaYZ
 * @since 2026-05-07
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
