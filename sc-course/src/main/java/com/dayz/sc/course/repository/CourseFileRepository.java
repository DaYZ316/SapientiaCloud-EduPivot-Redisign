package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.CourseFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 课程文件持久化边界。
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface CourseFileRepository {

    Optional<CourseFile> findById(UUID id);

    CourseFile save(CourseFile courseFile);

    void deleteById(UUID id);

    List<CourseFile> findByCourseId(UUID courseId);
}
