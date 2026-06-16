package com.dayz.sc.auth.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.auth.mapper.StudentMapper;
import com.dayz.sc.auth.model.entity.Student;
import com.dayz.sc.auth.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 MyBatis-Plus 的学生仓储实现
 *
 * @author DaYZ
 * @since 2026-06-09
 */
@Repository
@RequiredArgsConstructor
public class MybatisStudentRepository implements StudentRepository {

    private final StudentMapper studentMapper;

    @Override
    public Optional<Student> findById(UUID id) {
        return Optional.ofNullable(studentMapper.selectById(id));
    }

    @Override
    public Optional<Student> findByUserId(UUID userId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getUserId, userId);
        return Optional.ofNullable(studentMapper.selectOne(wrapper));
    }

    @Override
    public List<Student> findByUserIds(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Student::getUserId, userIds);
        return studentMapper.selectList(wrapper);
    }

    @Override
    public Optional<Student> findByStudentNo(String studentNo) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Student::getStudentNo, studentNo);
        return Optional.ofNullable(studentMapper.selectOne(wrapper));
    }

    @Override
    public void save(Student student) {
        studentMapper.insert(student);
    }

    @Override
    public void update(Student student) {
        studentMapper.updateById(student);
    }
}
