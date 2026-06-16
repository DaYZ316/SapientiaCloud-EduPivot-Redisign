package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Course;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface CourseRepository {

    Optional<Course> findById(UUID id);

    Optional<Course> findByIdForUpdate(UUID id);

    void save(Course course);

    void update(Course course);

    void deleteById(UUID id);

    /**
     * 分页查询课程（自动 count）。
     */
    Page<Course> findAll(int page, int size, String keyword, Integer level, Integer status, Integer isPublic,
                         Instant createdAtStart, Instant createdAtEnd,
                         Instant updatedAtStart, Instant updatedAtEnd);

    List<Course> findByIds(List<UUID> ids);

    /**
     * 分页查询教师课程（自动 count）。
     */
    Page<Course> findTeacherCourses(UUID teacherId, String role, int page, int size);
}
