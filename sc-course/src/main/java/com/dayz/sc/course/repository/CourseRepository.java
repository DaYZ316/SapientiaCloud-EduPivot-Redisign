package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Course;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface CourseRepository {

    /**
     * 根据ID查询课程
     *
     * @param id 课程ID
     * @return 课程实体，可能为空
     */
    Optional<Course> findById(UUID id);

    /**
     * 根据ID查询课程（加锁）
     *
     * @param id 课程ID
     * @return 课程实体，可能为空
     */
    Optional<Course> findByIdForUpdate(UUID id);

    /**
     * 保存课程
     *
     * @param course 课程实体
     */
    void save(Course course);

    /**
     * 更新课程
     *
     * @param course 课程实体
     */
    void update(Course course);

    /**
     * 根据ID删除课程
     *
     * @param id 课程ID
     */
    void deleteById(UUID id);

    /**
     * 分页查询课程（自动 count）
     *
     * @param page           页码
     * @param size           每页大小
     * @param keyword        关键词
     * @param level          课程级别
     * @param status         课程状态
     * @param isPublic       是否公开
     * @param createdAtStart 创建时间开始
     * @param createdAtEnd   创建时间结束
     * @param updatedAtStart 更新时间开始
     * @param updatedAtEnd   更新时间结束
     * @return 分页结果
     */
    Page<Course> findAll(int page, int size, String keyword, Integer level, Integer status, Integer isPublic,
                         Instant createdAtStart, Instant createdAtEnd,
                         Instant updatedAtStart, Instant updatedAtEnd);

    /**
     * 根据ID列表批量查询课程
     *
     * @param ids 课程ID列表
     * @return 课程列表
     */
    List<Course> findByIds(List<UUID> ids);

    List<UUID> findAllIds();

    List<UUID> findTeacherCourseIds(UUID teacherId);

    /**
     * 分页查询教师课程（自动 count）
     *
     * @param teacherId 教师ID
     * @param role      教师角色
     * @param page      页码
     * @param size      每页大小
     * @return 分页结果
     */
    Page<Course> findTeacherCourses(UUID teacherId, String role, int page, int size);
}
