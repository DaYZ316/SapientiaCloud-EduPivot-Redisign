package com.dayz.sc.course.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CourseTeacherRepository {

    List<UUID> findTeacherIdsByCourseId(UUID courseId);

    Map<UUID, List<UUID>> findTeacherIdsByCourseIds(List<UUID> courseIds);

    void batchSave(UUID courseId, List<UUID> teacherIds);

    void deleteByCourseId(UUID courseId);

    void deleteByCourseIdAndTeacherIds(UUID courseId, List<UUID> teacherIds);

    boolean existsByCourseIdAndTeacherId(UUID courseId, UUID teacherId);
}
