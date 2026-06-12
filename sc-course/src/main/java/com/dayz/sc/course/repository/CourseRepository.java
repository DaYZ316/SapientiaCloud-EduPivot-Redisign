package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.Course;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRepository {

    Optional<Course> findById(UUID id);

    Optional<Course> findByIdForUpdate(UUID id);

    void save(Course course);

    void update(Course course);

    void deleteById(UUID id);

    List<Course> findAll(int page, int size, String keyword, Integer level, Integer status, Integer isPublic,
                         Instant createdAtStart, Instant createdAtEnd,
                         Instant updatedAtStart, Instant updatedAtEnd);

    List<Course> findByIds(List<UUID> ids);

    List<Course> findTeacherCourses(UUID teacherId, String role, int page, int size);

    long countTeacherCourses(UUID teacherId, String role);

    long countAll(String keyword, Integer level, Integer status, Integer isPublic,
                  Instant createdAtStart, Instant createdAtEnd,
                  Instant updatedAtStart, Instant updatedAtEnd);
}
