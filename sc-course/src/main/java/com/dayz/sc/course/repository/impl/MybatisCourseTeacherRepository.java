package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.mapper.CourseTeacherMapper;
import com.dayz.sc.course.model.entity.CourseTeacher;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Repository
@RequiredArgsConstructor
public class MybatisCourseTeacherRepository implements CourseTeacherRepository {

    private final CourseTeacherMapper courseTeacherMapper;

    @Override
    public List<UUID> findTeacherIdsByCourseId(UUID courseId) {
        LambdaQueryWrapper<CourseTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseTeacher::getCourseId, courseId);
        wrapper.select(CourseTeacher::getTeacherId);
        return courseTeacherMapper.selectList(wrapper).stream()
                .map(CourseTeacher::getTeacherId)
                .toList();
    }

    @Override
    public Map<UUID, List<UUID>> findTeacherIdsByCourseIds(List<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        LambdaQueryWrapper<CourseTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CourseTeacher::getCourseId, courseIds);
        wrapper.select(CourseTeacher::getCourseId, CourseTeacher::getTeacherId);
        return courseTeacherMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(
                        CourseTeacher::getCourseId,
                        Collectors.mapping(CourseTeacher::getTeacherId, Collectors.toList())
                ));
    }

    @Override
    public void batchSave(UUID courseId, List<UUID> teacherIds) {
        List<CourseTeacher> entities = teacherIds.stream().map(teacherId -> {
            CourseTeacher ct = new CourseTeacher();
            ct.setId(UuidV7Generator.generate());
            ct.setCourseId(courseId);
            ct.setTeacherId(teacherId);
            return ct;
        }).toList();
        courseTeacherMapper.batchInsert(entities);
    }

    @Override
    public void deleteByCourseId(UUID courseId) {
        LambdaQueryWrapper<CourseTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseTeacher::getCourseId, courseId);
        courseTeacherMapper.delete(wrapper);
    }

    @Override
    public void deleteByCourseIdAndTeacherIds(UUID courseId, List<UUID> teacherIds) {
        if (teacherIds == null || teacherIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<CourseTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseTeacher::getCourseId, courseId);
        wrapper.in(CourseTeacher::getTeacherId, teacherIds);
        courseTeacherMapper.delete(wrapper);
    }

    @Override
    public boolean existsByCourseIdAndTeacherId(UUID courseId, UUID teacherId) {
        LambdaQueryWrapper<CourseTeacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseTeacher::getCourseId, courseId);
        wrapper.eq(CourseTeacher::getTeacherId, teacherId);
        return courseTeacherMapper.selectCount(wrapper) > 0;
    }
}
