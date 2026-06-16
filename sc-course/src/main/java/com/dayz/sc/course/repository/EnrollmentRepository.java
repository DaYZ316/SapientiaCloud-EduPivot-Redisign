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

    /**
     * 根据ID查询选课记录。
     *
     * @param id 选课记录ID
     * @return 选课记录实体，可能为空
     */
    Optional<Enrollment> findById(UUID id);

    /**
     * 根据课程ID和学生ID查询选课记录。
     *
     * @param courseId  课程ID
     * @param studentId 学生ID
     * @return 选课记录实体，可能为空
     */
    Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId);

    /**
     * 保存选课记录。
     *
     * @param enrollment 选课记录实体
     */
    void save(Enrollment enrollment);

    /**
     * 更新选课记录。
     *
     * @param enrollment 选课记录实体
     */
    void update(Enrollment enrollment);

    /**
     * 根据学生ID分页查询选课记录。
     *
     * @param studentId 学生ID
     * @param page      页码
     * @param size      每页大小
     * @return 分页结果
     */
    Page<Enrollment> findByStudentId(UUID studentId, int page, int size);

    /**
     * 根据课程ID分页查询选课记录。
     *
     * @param courseId 课程ID
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    Page<Enrollment> findByCourseId(UUID courseId, int page, int size);

    /**
     * 统计课程的活跃选课人数。
     *
     * @param courseId 课程ID
     * @return 活跃选课人数
     */
    long countActiveByCourseId(UUID courseId);

    /**
     * 批量统计课程的活跃选课人数。
     *
     * @param courseIds 课程ID列表
     * @return 课程ID与活跃选课人数的映射
     */
    Map<UUID, Long> countActiveByCourseIds(List<UUID> courseIds);
}
