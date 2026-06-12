package com.dayz.sc.auth.repository;

import com.dayz.sc.auth.model.entity.Student;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学生仓储接口。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public interface StudentRepository {

    Optional<Student> findById(UUID id);

    Optional<Student> findByUserId(UUID userId);

    List<Student> findByUserIds(List<UUID> userIds);

    Optional<Student> findByStudentNo(String studentNo);

    void save(Student student);

    void update(Student student);
}
