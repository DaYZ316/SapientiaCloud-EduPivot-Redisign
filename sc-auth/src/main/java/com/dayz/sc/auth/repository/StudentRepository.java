package com.dayz.sc.auth.repository;

import com.dayz.sc.auth.model.entity.Student;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 学生仓储接口
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public interface StudentRepository {

    /**
     * 根据ID查询学生
     *
     * @param id 学生ID
     * @return 学生实体，可能为空
     */
    Optional<Student> findById(UUID id);

    /**
     * 根据用户ID查询学生
     *
     * @param userId 用户ID
     * @return 学生实体，可能为空
     */
    Optional<Student> findByUserId(UUID userId);

    /**
     * 根据用户ID列表批量查询学生
     *
     * @param userIds 用户ID列表
     * @return 学生列表
     */
    List<Student> findByUserIds(List<UUID> userIds);

    /**
     * 根据学号查询学生
     *
     * @param studentNo 学号
     * @return 学生实体，可能为空
     */
    Optional<Student> findByStudentNo(String studentNo);

    /**
     * 保存学生
     *
     * @param student 学生实体
     */
    void save(Student student);

    /**
     * 更新学生
     *
     * @param student 学生实体
     */
    void update(Student student);
}
