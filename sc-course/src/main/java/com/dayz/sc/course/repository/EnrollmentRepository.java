package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.Enrollment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {

    Optional<Enrollment> findById(UUID id);

    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    void save(Enrollment enrollment);

    void update(Enrollment enrollment);

    List<Enrollment> findByStudentId(UUID studentId, int page, int size);

    List<Enrollment> findByCourseId(UUID courseId, int page, int size);

    long countByStudentId(UUID studentId);

    long countByCourseId(UUID courseId);

    long countActiveByCourseId(UUID courseId);

    Map<UUID, Long> countActiveByCourseIds(List<UUID> courseIds);
}
