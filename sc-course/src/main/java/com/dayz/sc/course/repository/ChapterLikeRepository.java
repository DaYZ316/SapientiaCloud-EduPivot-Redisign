package com.dayz.sc.course.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public interface ChapterLikeRepository {

    /**
     * 用户点赞章节
     *
     * @param chapterId 章节ID
     * @param courseId  课程ID
     * @param userId    用户ID
     * @return 是否点赞成功
     */
    boolean like(UUID chapterId, UUID courseId, UUID userId);

    /**
     * 用户取消点赞章节
     *
     * @param chapterId 章节ID
     * @param userId    用户ID
     * @return 是否取消成功
     */
    boolean unlike(UUID chapterId, UUID userId);

    /**
     * 检查用户是否已点赞指定章节
     *
     * @param chapterId 章节ID
     * @param userId    用户ID
     * @return 是否已点赞
     */
    boolean existsByChapterIdAndUserId(UUID chapterId, UUID userId);

    /**
     * 批量查询用户已点赞的章节ID集合
     *
     * @param userId     用户ID
     * @param chapterIds 章节ID集合
     * @return 已点赞的章节ID集合
     */
    Set<UUID> findLikedChapterIds(UUID userId, Collection<UUID> chapterIds);
}
