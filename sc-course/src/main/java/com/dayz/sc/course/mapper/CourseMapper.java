package com.dayz.sc.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayz.sc.course.model.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus Mapper 接口。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {}
