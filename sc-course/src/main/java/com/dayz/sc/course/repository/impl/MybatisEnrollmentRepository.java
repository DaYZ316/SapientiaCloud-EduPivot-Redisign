package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dayz.sc.course.mapper.EnrollmentMapper;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MybatisEnrollmentRepository implements EnrollmentRepository {

    private final EnrollmentMapper enrollmentMapper;

    @Override
    public Optional<Enrollment> findById(UUID id) {
        return Optional.ofNullable(enrollmentMapper.selectById(id));
    }

    @Override
    public Optional<Enrollment> findByCourseIdAndStudentId(UUID courseId, UUID studentId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId);
        wrapper.eq(Enrollment::getStudentId, studentId);
        return Optional.ofNullable(enrollmentMapper.selectOne(wrapper));
    }

    @Override
    public void save(Enrollment enrollment) {
        enrollmentMapper.insert(enrollment);
    }

    @Override
    public void update(Enrollment enrollment) {
        enrollmentMapper.updateById(enrollment);
    }

    @Override
    public List<Enrollment> findByStudentId(UUID studentId, int page, int size) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getStudentId, studentId);
        wrapper.orderByDesc(Enrollment::getEnrolledAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return enrollmentMapper.selectList(wrapper);
    }

    @Override
    public List<Enrollment> findByCourseId(UUID courseId, int page, int size) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId);
        wrapper.orderByDesc(Enrollment::getEnrolledAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return enrollmentMapper.selectList(wrapper);
    }

    @Override
    public long countByStudentId(UUID studentId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getStudentId, studentId);
        return enrollmentMapper.selectCount(wrapper);
    }

    @Override
    public long countByCourseId(UUID courseId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId);
        return enrollmentMapper.selectCount(wrapper);
    }

    @Override
    public long countActiveByCourseId(UUID courseId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getCourseId, courseId);
        wrapper.eq(Enrollment::getStatus, EnrollmentStatus.ACTIVE.getCode());
        return enrollmentMapper.selectCount(wrapper);
    }

    @Override
    public Map<UUID, Long> countActiveByCourseIds(List<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }

        QueryWrapper<Enrollment> wrapper = new QueryWrapper<>();
        wrapper.select("course_id", "COUNT(*) AS active_count")
                .in("course_id", courseIds)
                .eq("status", EnrollmentStatus.ACTIVE.getCode())
                .groupBy("course_id");

        return enrollmentMapper.selectMaps(wrapper).stream()
                .collect(Collectors.toMap(
                        row -> toUuid(value(row, "course_id", "courseId")),
                        row -> toLong(value(row, "active_count", "activeCount"))
                ));
    }

    private Object value(Map<String, Object> row, String snakeCaseKey, String camelCaseKey) {
        Object value = row.get(snakeCaseKey);
        return value != null ? value : row.get(camelCaseKey);
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(value.toString());
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
