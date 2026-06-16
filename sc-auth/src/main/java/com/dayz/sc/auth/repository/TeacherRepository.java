package com.dayz.sc.auth.repository;

import com.dayz.sc.auth.model.entity.Teacher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 教师仓储接口
 *
 * @author DaYZ
 * @since 2026-06-09
 */
public interface TeacherRepository {

    /**
     * 根据ID查询教师。
     *
     * @param id 教师ID
     * @return 教师实体，可能为空
     */
    Optional<Teacher> findById(UUID id);

    /**
     * 根据用户ID查询教师。
     *
     * @param userId 用户ID
     * @return 教师实体，可能为空
     */
    Optional<Teacher> findByUserId(UUID userId);

    /**
     * 根据用户ID列表批量查询教师。
     *
     * @param userIds 用户ID列表
     * @return 教师列表
     */
    List<Teacher> findByUserIds(List<UUID> userIds);

    /**
     * 根据工号查询教师。
     *
     * @param employeeNo 工号
     * @return 教师实体，可能为空
     */
    Optional<Teacher> findByEmployeeNo(String employeeNo);

    /**
     * 保存教师。
     *
     * @param teacher 教师实体
     */
    void save(Teacher teacher);

    /**
     * 更新教师。
     *
     * @param teacher 教师实体
     */
    void update(Teacher teacher);
}
