package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.CourseMapper;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Repository
@RequiredArgsConstructor
public class MybatisCourseRepository implements CourseRepository {

    private static final String ROLE_PRIMARY = "primary";
    private static final String ROLE_ASSISTANT = "assistant";

    private final CourseMapper courseMapper;

    @Override
    public Optional<Course> findById(UUID id) {
        return Optional.ofNullable(courseMapper.selectById(id));
    }

    @Override
    public Optional<Course> findByIdForUpdate(UUID id) {
        QueryWrapper<Course> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).last("FOR UPDATE");
        return Optional.ofNullable(courseMapper.selectOne(wrapper));
    }

    @Override
    public void save(Course course) {
        courseMapper.insert(course);
    }

    @Override
    public void update(Course course) {
        courseMapper.updateById(course);
    }

    @Override
    public void deleteById(UUID id) {
        courseMapper.deleteById(id);
    }

    @Override
    public Page<Course> findAll(int page, int size, String keyword, Integer level, Integer status, Integer isPublic,
                                Instant createdAtStart, Instant createdAtEnd,
                                Instant updatedAtStart, Instant updatedAtEnd) {
        LambdaQueryWrapper<Course> wrapper = buildFilterWrapper(keyword, level, status, isPublic,
                createdAtStart, createdAtEnd, updatedAtStart, updatedAtEnd);
        wrapper.orderByDesc(Course::getCreatedAt);
        return courseMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Course> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Course::getId, ids);
        return courseMapper.selectList(wrapper);
    }

    @Override
    public Page<Course> findTeacherCourses(UUID teacherId, String role, int page, int size) {
        LambdaQueryWrapper<Course> wrapper = teacherCourseWrapper(teacherId, role);
        wrapper.orderByDesc(Course::getCreatedAt);
        return courseMapper.selectPage(new Page<>(page, size), wrapper);
    }

    // ==================== Private ====================

    private LambdaQueryWrapper<Course> buildFilterWrapper(String keyword, Integer level, Integer status,
                                                          Integer isPublic, Instant createdAtStart,
                                                          Instant createdAtEnd, Instant updatedAtStart,
                                                          Instant updatedAtEnd) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Course::getTitle, keyword)
                    .or()
                    .like(Course::getDescription, keyword)
            );
        }
        if (level != null) {
            wrapper.eq(Course::getLevel, level);
        }
        if (status != null) {
            wrapper.eq(Course::getStatus, status);
        }
        if (isPublic != null) {
            wrapper.eq(Course::getIsPublic, isPublic);
        }
        if (createdAtStart != null) {
            wrapper.ge(Course::getCreatedAt, createdAtStart);
        }
        if (createdAtEnd != null) {
            wrapper.le(Course::getCreatedAt, createdAtEnd);
        }
        if (updatedAtStart != null) {
            wrapper.ge(Course::getUpdatedAt, updatedAtStart);
        }
        if (updatedAtEnd != null) {
            wrapper.le(Course::getUpdatedAt, updatedAtEnd);
        }
        return wrapper;
    }

    private LambdaQueryWrapper<Course> teacherCourseWrapper(UUID teacherId, String role) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        if (ROLE_PRIMARY.equalsIgnoreCase(role)) {
            wrapper.eq(Course::getTeacherId, teacherId);
            return wrapper;
        }
        if (ROLE_ASSISTANT.equalsIgnoreCase(role)) {
            wrapper.ne(Course::getTeacherId, teacherId);
            wrapper.exists("SELECT 1 FROM edu_course_teacher ect WHERE ect.course_id = edu_course.id AND ect.teacher_id = {0}", teacherId);
            return wrapper;
        }
        wrapper.exists("SELECT 1 FROM edu_course_teacher ect WHERE ect.course_id = edu_course.id AND ect.teacher_id = {0}", teacherId);
        return wrapper;
    }
}
