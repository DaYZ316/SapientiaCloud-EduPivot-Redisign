package com.dayz.sc.course.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface ChapterLikeRepository {

    boolean like(UUID chapterId, UUID courseId, UUID userId);

    boolean unlike(UUID chapterId, UUID userId);

    boolean existsByChapterIdAndUserId(UUID chapterId, UUID userId);

    Set<UUID> findLikedChapterIds(UUID userId, Collection<UUID> chapterIds);
}
