package com.dayz.sc.course.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * 数据访问接口。
 *
 * @author DaYZ
 * @since 2026-06-14
 */
public interface ChapterLikeRepository {

    boolean like(UUID chapterId, UUID courseId, UUID userId);

    boolean unlike(UUID chapterId, UUID userId);

    boolean existsByChapterIdAndUserId(UUID chapterId, UUID userId);

    Set<UUID> findLikedChapterIds(UUID userId, Collection<UUID> chapterIds);
}
