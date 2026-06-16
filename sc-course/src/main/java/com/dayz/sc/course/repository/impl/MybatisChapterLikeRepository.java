package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.mapper.ChapterLikeMapper;
import com.dayz.sc.course.model.entity.ChapterLike;
import com.dayz.sc.course.repository.ChapterLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Repository
@RequiredArgsConstructor
public class MybatisChapterLikeRepository implements ChapterLikeRepository {

    private final ChapterLikeMapper chapterLikeMapper;

    @Override
    public boolean like(UUID chapterId, UUID courseId, UUID userId) {
        ChapterLike like = new ChapterLike();
        like.setId(UuidV7Generator.generate());
        like.setChapterId(chapterId);
        like.setCourseId(courseId);
        like.setUserId(userId);
        like.setCreatedAt(Instant.now());
        try {
            chapterLikeMapper.insert(like);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public boolean unlike(UUID chapterId, UUID userId) {
        LambdaQueryWrapper<ChapterLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterLike::getChapterId, chapterId);
        wrapper.eq(ChapterLike::getUserId, userId);
        return chapterLikeMapper.delete(wrapper) > 0;
    }

    @Override
    public boolean existsByChapterIdAndUserId(UUID chapterId, UUID userId) {
        LambdaQueryWrapper<ChapterLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterLike::getChapterId, chapterId);
        wrapper.eq(ChapterLike::getUserId, userId);
        return chapterLikeMapper.selectCount(wrapper) > 0;
    }

    @Override
    public Set<UUID> findLikedChapterIds(UUID userId, Collection<UUID> chapterIds) {
        if (chapterIds.isEmpty()) {
            return Set.of();
        }
        LambdaQueryWrapper<ChapterLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChapterLike::getUserId, userId);
        wrapper.in(ChapterLike::getChapterId, chapterIds);
        wrapper.select(ChapterLike::getChapterId);
        return chapterLikeMapper.selectList(wrapper).stream()
                .map(ChapterLike::getChapterId)
                .collect(Collectors.toSet());
    }
}
