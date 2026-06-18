package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ForumPost;

import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface ForumPostRepository {

    /**
     * 根据ID查询论坛帖子
     *
     * @param id 帖子ID
     * @return 帖子实体，可能为空
     */
    Optional<ForumPost> findById(UUID id);

    /**
     * 保存论坛帖子
     *
     * @param post 帖子实体
     */
    void save(ForumPost post);

    /**
     * 更新论坛帖子
     *
     * @param post 帖子实体
     */
    void update(ForumPost post);

    /**
     * 根据ID删除论坛帖子
     *
     * @param id 帖子ID
     */
    void deleteById(UUID id);

    /**
     * 分页查询论坛帖子
     *
     * @param page     页码
     * @param size     每页大小
     * @param courseId 课程ID
     * @param status   帖子状态
     * @param keyword  关键词
     * @return 分页结果
     */
    Page<ForumPost> findAll(int page, int size, UUID courseId, Integer status, String keyword);
}
