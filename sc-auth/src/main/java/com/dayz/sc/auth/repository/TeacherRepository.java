package com.dayz.sc.auth.repository;

import com.dayz.sc.auth.model.entity.Teacher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 教师仓储接口。
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public interface TeacherRepository {

    Optional<Teacher> findById(UUID id);

    Optional<Teacher> findByUserId(UUID userId);

    List<Teacher> findByUserIds(List<UUID> userIds);

    Optional<Teacher> findByEmployeeNo(String employeeNo);

    void save(Teacher teacher);

    void update(Teacher teacher);
}
