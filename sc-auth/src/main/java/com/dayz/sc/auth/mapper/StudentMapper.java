package com.dayz.sc.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.auth.model.entity.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生 MyBatis Mapper。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
