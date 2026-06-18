package com.dayz.sc.course.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface CourseTeacherRepository {

    /**
     * 根据课程ID查询教师ID列表
     *
     * @param courseId 课程ID
     * @return 教师ID列表
     */
    List<UUID> findTeacherIdsByCourseId(UUID courseId);

    /**
     * 批量查询课程的教师ID列表
     *
     * @param courseIds 课程ID列表
     * @return 课程ID与教师ID列表的映射
     */
    Map<UUID, List<UUID>> findTeacherIdsByCourseIds(List<UUID> courseIds);

    /**
     * 批量保存课程教师关联
     *
     * @param courseId   课程ID
     * @param teacherIds 教师ID列表
     */
    void batchSave(UUID courseId, List<UUID> teacherIds);

    /**
     * 根据课程ID删除所有教师关联
     *
     * @param courseId 课程ID
     */
    void deleteByCourseId(UUID courseId);

    /**
     * 根据课程ID和教师ID列表删除教师关联
     *
     * @param courseId   课程ID
     * @param teacherIds 教师ID列表
     */
    void deleteByCourseIdAndTeacherIds(UUID courseId, List<UUID> teacherIds);

    /**
     * 检查课程是否存在指定教师
     *
     * @param courseId  课程ID
     * @param teacherId 教师ID
     * @return 是否存在
     */
    boolean existsByCourseIdAndTeacherId(UUID courseId, UUID teacherId);
}
