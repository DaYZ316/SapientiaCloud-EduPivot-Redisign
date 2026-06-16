package com.dayz.sc.auth.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.auth.mapper.TeacherMapper;
import com.dayz.sc.auth.model.entity.Teacher;
import com.dayz.sc.auth.repository.TeacherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 MyBatis-Plus 的教师仓储实现
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Repository
@RequiredArgsConstructor
public class MybatisTeacherRepository implements TeacherRepository {

    private final TeacherMapper teacherMapper;

    @Override
    public Optional<Teacher> findById(UUID id) {
        return Optional.ofNullable(teacherMapper.selectById(id));
    }

    @Override
    public Optional<Teacher> findByUserId(UUID userId) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getUserId, userId);
        return Optional.ofNullable(teacherMapper.selectOne(wrapper));
    }

    @Override
    public List<Teacher> findByUserIds(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Teacher::getUserId, userIds);
        return teacherMapper.selectList(wrapper);
    }

    @Override
    public Optional<Teacher> findByEmployeeNo(String employeeNo) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getEmployeeNo, employeeNo);
        return Optional.ofNullable(teacherMapper.selectOne(wrapper));
    }

    @Override
    public void save(Teacher teacher) {
        teacherMapper.insert(teacher);
    }

    @Override
    public void update(Teacher teacher) {
        teacherMapper.updateById(teacher);
    }
}
