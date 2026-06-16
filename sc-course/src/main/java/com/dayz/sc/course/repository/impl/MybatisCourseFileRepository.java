package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.CourseFileMapper;
import com.dayz.sc.course.model.entity.CourseFile;
import com.dayz.sc.course.repository.CourseFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 基于 MyBatis-Plus 的课程文件仓储实现
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Repository
@RequiredArgsConstructor
public class MybatisCourseFileRepository implements CourseFileRepository {

    private final CourseFileMapper courseFileMapper;

    @Override
    public Optional<CourseFile> findById(UUID id) {
        return Optional.ofNullable(courseFileMapper.selectById(id));
    }

    @Override
    public CourseFile save(CourseFile courseFile) {
        courseFileMapper.insert(courseFile);
        return courseFile;
    }

    @Override
    public void deleteById(UUID id) {
        courseFileMapper.deleteById(id);
    }

    @Override
    public Page<CourseFile> findByCourseId(UUID courseId, int page, int size) {
        LambdaQueryWrapper<CourseFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseFile::getCourseId, courseId)
                .orderByAsc(CourseFile::getSortOrder)
                .orderByDesc(CourseFile::getCreatedAt);
        return courseFileMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
