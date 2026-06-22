package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ClassSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for class sessions.
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public interface ClassSessionRepository {

    /**
     * 根据ID查询课堂会话
     *
     * @param id 课堂会话ID
     * @return 课堂会话实体，可能为空
     */
    Optional<ClassSession> findById(UUID id);

    /**
     * 根据ID查询课堂会话（加锁）
     *
     * @param id 课堂会话ID
     * @return 课堂会话实体，可能为空
     */
    Optional<ClassSession> findByIdForUpdate(UUID id);

    /**
     * 保存课堂会话
     *
     * @param session 课堂会话实体
     */
    void save(ClassSession session);

    /**
     * 更新课堂会话
     *
     * @param session 课堂会话实体
     */
    void update(ClassSession session);

    /**
     * 根据ID删除课堂会话
     *
     * @param id 课堂会话ID
     */
    void deleteById(UUID id);

    /**
     * 根据课程ID分页查询课堂会话
     *
     * @param courseId      课程ID
     * @param page          页码
     * @param size          每页大小
     * @param includeDrafts 是否包含草稿
     * @return 分页结果
     */
    Page<ClassSession> findByCourseId(UUID courseId, int page, int size, boolean includeDrafts);

    /**
     * 批量统计课程已发布开课数量
     *
     * @param courseIds 课程ID列表
     * @return 课程ID与已发布开课数量的映射
     */
    Map<UUID, Long> countPublishedByCourseIds(List<UUID> courseIds);

    List<ClassSession> findLiveSessionsPastEnd(Instant now, int endedLiveStatus, int limit);
}
