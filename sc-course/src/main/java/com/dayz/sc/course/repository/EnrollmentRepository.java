package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Enrollment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface EnrollmentRepository {

    Optional<Enrollment> findById(UUID id);

    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    void save(Enrollment enrollment);

    void update(Enrollment enrollment);

    Page<Enrollment> findByStudentId(UUID studentId, int page, int size);

    Page<Enrollment> findByCourseId(UUID courseId, int page, int size);

    long countActiveByCourseId(UUID courseId);

    Map<UUID, Long> countActiveByCourseIds(List<UUID> courseIds);
}
