package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Chapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
public interface ChapterRepository {

    /**
     * 根据ID查询章节。
     *
     * @param id 章节ID
     * @return 章节实体，可能为空
     */
    Optional<Chapter> findById(UUID id);

    /**
     * 保存章节。
     *
     * @param chapter 章节实体
     */
    void save(Chapter chapter);

    /**
     * 更新章节。
     *
     * @param chapter 章节实体
     */
    void update(Chapter chapter);

    /**
     * 根据ID删除章节。
     *
     * @param id 章节ID
     */
    void deleteById(UUID id);

    /**
     * 根据课程ID查询所有章节。
     *
     * @param courseId 课程ID
     * @return 章节列表
     */
    List<Chapter> findByCourseId(UUID courseId);

    /**
     * 根据课程ID查询根章节。
     *
     * @param courseId 课程ID
     * @return 根章节列表
     */
    List<Chapter> findRootByCourseId(UUID courseId);

    /**
     * 根据父章节ID查询子章节。
     *
     * @param parentChapterId 父章节ID
     * @return 子章节列表
     */
    List<Chapter> findByParentChapterId(UUID parentChapterId);

    /**
     * 分页查询章节。
     *
     * @param page     页码
     * @param size     每页大小
     * @param courseId 课程ID
     * @param status   章节状态
     * @param keyword  关键词
     * @return 分页结果
     */
    Page<Chapter> findAll(int page, int size, UUID courseId, Integer status, String keyword);

    /**
     * 统计课程的章节数量。
     *
     * @param courseId 课程ID
     * @return 章节数量
     */
    long countByCourseId(UUID courseId);

    /**
     * 检查课程中是否存在指定名称的章节。
     *
     * @param courseId    课程ID
     * @param chapterName 章节名称
     * @return 是否存在
     */
    boolean existsByCourseIdAndChapterName(UUID courseId, String chapterName);

    /**
     * 增加章节浏览次数。
     *
     * @param id 章节ID
     */
    void incrementViewCount(UUID id);

    /**
     * 增加章节点赞次数。
     *
     * @param id 章节ID
     */
    void incrementLikeCount(UUID id);

    /**
     * 减少章节点赞次数。
     *
     * @param id 章节ID
     */
    void decrementLikeCount(UUID id);
}
