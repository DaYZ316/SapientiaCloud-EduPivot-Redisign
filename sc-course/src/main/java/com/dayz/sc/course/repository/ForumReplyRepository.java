package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.ForumReply;

import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface ForumReplyRepository {

    /**
     * 根据ID查询论坛回复
     *
     * @param id 回复ID
     * @return 回复实体，可能为空
     */
    Optional<ForumReply> findById(UUID id);

    /**
     * 保存论坛回复
     *
     * @param reply 回复实体
     */
    void save(ForumReply reply);

    /**
     * 更新论坛回复
     *
     * @param reply 回复实体
     */
    void update(ForumReply reply);

    /**
     * 根据ID删除论坛回复
     *
     * @param id 回复ID
     */
    void deleteById(UUID id);

    /**
     * 根据帖子ID分页查询回复
     *
     * @param postId 帖子ID
     * @param page   页码
     * @param size   每页大小
     * @return 分页结果
     */
    Page<ForumReply> findByPostId(UUID postId, int page, int size);

    /**
     * 查询帖子的最大楼层数
     *
     * @param postId 帖子ID
     * @return 最大楼层数
     */
    int findMaxFloorNumber(UUID postId);
}
