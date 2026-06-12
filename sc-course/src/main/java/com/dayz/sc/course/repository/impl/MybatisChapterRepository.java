package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ChapterMapper;
import com.dayz.sc.course.model.entity.Chapter;
import com.dayz.sc.course.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MybatisChapterRepository implements ChapterRepository {

    private final ChapterMapper chapterMapper;

    @Override
    public Optional<Chapter> findById(UUID id) {
        return Optional.ofNullable(chapterMapper.selectById(id));
    }

    @Override
    public void save(Chapter chapter) {
        chapterMapper.insert(chapter);
    }

    @Override
    public void update(Chapter chapter) {
        chapterMapper.updateById(chapter);
    }

    @Override
    public void deleteById(UUID id) {
        chapterMapper.deleteById(id);
    }

    @Override
    public List<Chapter> findByCourseId(UUID courseId) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getCourseId, courseId);
        wrapper.orderByAsc(Chapter::getSortOrder);
        return chapterMapper.selectList(wrapper);
    }

    @Override
    public List<Chapter> findRootByCourseId(UUID courseId) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getCourseId, courseId);
        wrapper.isNull(Chapter::getParentChapterId);
        wrapper.orderByAsc(Chapter::getSortOrder);
        return chapterMapper.selectList(wrapper);
    }

    @Override
    public List<Chapter> findByParentChapterId(UUID parentChapterId) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getParentChapterId, parentChapterId);
        wrapper.orderByAsc(Chapter::getSortOrder);
        return chapterMapper.selectList(wrapper);
    }

    @Override
    public Page<Chapter> findAll(int page, int size, UUID courseId, Integer status, String keyword) {
        LambdaQueryWrapper<Chapter> wrapper = buildFilterWrapper(courseId, status, keyword);
        wrapper.orderByAsc(Chapter::getSortOrder);
        return chapterMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public long countByCourseId(UUID courseId) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getCourseId, courseId);
        return chapterMapper.selectCount(wrapper);
    }

    @Override
    public boolean existsByCourseIdAndChapterName(UUID courseId, String chapterName) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Chapter::getCourseId, courseId);
        wrapper.eq(Chapter::getChapterName, chapterName);
        return chapterMapper.selectCount(wrapper) > 0;
    }

    private LambdaQueryWrapper<Chapter> buildFilterWrapper(UUID courseId, Integer status, String keyword) {
        LambdaQueryWrapper<Chapter> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            wrapper.eq(Chapter::getCourseId, courseId);
        }
        if (status != null) {
            wrapper.eq(Chapter::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Chapter::getChapterName, keyword);
        }
        return wrapper;
    }
}
