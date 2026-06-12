package com.dayz.sc.course.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.entity.Chapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChapterRepository {

    Optional<Chapter> findById(UUID id);

    void save(Chapter chapter);

    void update(Chapter chapter);

    void deleteById(UUID id);

    List<Chapter> findByCourseId(UUID courseId);

    List<Chapter> findRootByCourseId(UUID courseId);

    List<Chapter> findByParentChapterId(UUID parentChapterId);

    Page<Chapter> findAll(int page, int size, UUID courseId, Integer status, String keyword);

    long countByCourseId(UUID courseId);

    boolean existsByCourseIdAndChapterName(UUID courseId, String chapterName);
}
