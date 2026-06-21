package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.CourseFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 课程文件持久化边界
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface CourseFileRepository {

    /**
     * 根据ID查询课程文件
     *
     * @param id 文件ID
     * @return 文件实体，可能为空
     */
    Optional<CourseFile> findById(UUID id);

    /**
     * 保存课程文件
     *
     * @param courseFile 文件实体
     * @return 保存后的文件实体
     */
    CourseFile save(CourseFile courseFile);

    /**
     * 根据ID删除课程文件
     *
     * @param id 文件ID
     */
    void deleteById(UUID id);

    /**
     * 根据课程ID分页查询课程文件
     *
     * @param courseId 课程ID
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    Page<CourseFile> findByCourseId(UUID courseId, int page, int size);

    List<CourseFile> findAllByCourseId(UUID courseId);
}
