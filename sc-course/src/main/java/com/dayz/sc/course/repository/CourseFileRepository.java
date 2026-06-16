package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.CourseFile;

import java.util.Optional;
import java.util.UUID;

/**
 * 课程文件持久化边界
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface CourseFileRepository {

    Optional<CourseFile> findById(UUID id);

    CourseFile save(CourseFile courseFile);

    void deleteById(UUID id);

    Page<CourseFile> findByCourseId(UUID courseId, int page, int size);
}
