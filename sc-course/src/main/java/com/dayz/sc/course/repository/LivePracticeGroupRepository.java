package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.LivePracticeGroup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 随堂练习分组仓储
 *
 * @author DaYZ
 * @since 2026-06-18
 */
public interface LivePracticeGroupRepository {

    /**
     * 根据ID查询随堂练习分组
     *
     * @param id 分组ID
     * @return 分组实体，可能为空
     */
    Optional<LivePracticeGroup> findById(UUID id);

    /**
     * 保存随堂练习分组
     *
     * @param group 分组实体
     */
    void save(LivePracticeGroup group);

    /**
     * 根据课堂会话ID查询随堂练习分组列表
     *
     * @param classSessionId 课堂会话ID
     * @return 分组列表
     */
    List<LivePracticeGroup> findByClassSessionId(UUID classSessionId);

    /**
     * 根据课程ID查询随堂练习分组列表
     *
     * @param courseId 课程ID
     * @return 分组列表
     */
    List<LivePracticeGroup> findByCourseId(UUID courseId);

    /**
     * 统计课堂会话下的随堂练习分组数量
     *
     * @param classSessionId 课堂会话ID
     * @return 分组数量
     */
    long countByClassSessionId(UUID classSessionId);
}
