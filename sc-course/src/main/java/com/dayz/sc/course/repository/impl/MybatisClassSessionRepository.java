package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.mapper.ClassSessionMapper;
import com.dayz.sc.course.model.entity.ClassSession;
import com.dayz.sc.course.repository.ClassSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 课堂会话 MyBatis Repository 实现
 *
 * @author DaYZ
 * @since 2026-06-14
 */
@Repository
@RequiredArgsConstructor
public class MybatisClassSessionRepository implements ClassSessionRepository {

    private final ClassSessionMapper classSessionMapper;

    @Override
    public Optional<ClassSession> findById(UUID id) {
        return Optional.ofNullable(classSessionMapper.selectById(id));
    }

    @Override
    public Optional<ClassSession> findByIdForUpdate(UUID id) {
        QueryWrapper<ClassSession> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).last("FOR UPDATE");
        return Optional.ofNullable(classSessionMapper.selectOne(wrapper));
    }

    @Override
    public void save(ClassSession session) {
        classSessionMapper.insert(session);
    }

    @Override
    public void update(ClassSession session) {
        classSessionMapper.updateById(session);
    }

    @Override
    public void deleteById(UUID id) {
        classSessionMapper.deleteById(id);
    }

    @Override
    public Page<ClassSession> findByCourseId(UUID courseId, int page, int size, boolean includeDrafts) {
        LambdaQueryWrapper<ClassSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ClassSession::getCourseId, courseId);
        if (!includeDrafts) {
            wrapper.isNotNull(ClassSession::getPublishedAt);
        }
        wrapper.orderByDesc(ClassSession::getScheduledStartAt);
        return classSessionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<ClassSession> findOngoingByTeacherId(UUID teacherId, Instant now, int limit) {
        LambdaQueryWrapper<ClassSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ClassSession::getPublishedAt);
        wrapper.eq(ClassSession::getTeacherId, teacherId);
        wrapper.le(ClassSession::getScheduledStartAt, now);
        wrapper.gt(ClassSession::getScheduledEndAt, now);
        wrapper.orderByAsc(ClassSession::getScheduledEndAt);
        wrapper.last("LIMIT " + Math.max(1, limit));
        return classSessionMapper.selectList(wrapper);
    }

    @Override
    public List<ClassSession> findOngoingByStudentId(UUID studentId, int activeStatus, int completedStatus, Instant now, int limit) {
        LambdaQueryWrapper<ClassSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ClassSession::getPublishedAt);
        wrapper.le(ClassSession::getScheduledStartAt, now);
        wrapper.gt(ClassSession::getScheduledEndAt, now);
        wrapper.exists("SELECT 1 FROM edu_enrollment e WHERE e.course_id = edu_class_session.course_id AND e.student_id = {0} AND e.status IN ({1}, {2})", studentId, activeStatus, completedStatus);
        wrapper.orderByAsc(ClassSession::getScheduledEndAt);
        wrapper.last("LIMIT " + Math.max(1, limit));
        return classSessionMapper.selectList(wrapper);
    }

    @Override
    public Map<UUID, Long> countPublishedByCourseIds(List<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }

        QueryWrapper<ClassSession> wrapper = new QueryWrapper<>();
        wrapper.select("course_id", "COUNT(*) AS published_count")
                .in("course_id", courseIds)
                .isNotNull("published_at")
                .eq("deleted", 0)
                .groupBy("course_id");

        Map<UUID, Long> counts = new HashMap<>(courseIds.size());
        classSessionMapper.selectMaps(wrapper).forEach(row -> {
            Object courseId = value(row, "course_id", "courseId");
            Object count = value(row, "published_count", "publishedCount");
            if (courseId != null && count != null) {
                counts.put(toUuid(courseId), toLong(count));
            }
        });
        return counts;
    }

    @Override
    public List<ClassSession> findLiveSessionsPastEnd(Instant now, int endedLiveStatus, int limit) {
        LambdaQueryWrapper<ClassSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNotNull(ClassSession::getPublishedAt);
        wrapper.le(ClassSession::getScheduledEndAt, now);
        wrapper.ne(ClassSession::getLiveStatus, endedLiveStatus);
        wrapper.orderByAsc(ClassSession::getScheduledEndAt);
        wrapper.last("LIMIT " + Math.max(1, limit));
        return classSessionMapper.selectList(wrapper);
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
