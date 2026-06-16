package com.dayz.sc.course.repository;

import com.dayz.sc.course.model.entity.CourseInvitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口
 *
 * @author DaYZ
 * @since 2026-06-13
 */
public interface CourseInvitationRepository {

    void save(CourseInvitation invitation);

    void update(CourseInvitation invitation);

    Optional<CourseInvitation> findById(UUID id);

    Optional<CourseInvitation> findPendingByCourseIdAndInviteeId(UUID courseId, UUID inviteeId);

    boolean existsPendingByCourseIdAndInviteeId(UUID courseId, UUID inviteeId);

    List<CourseInvitation> findByInviteeId(UUID inviteeId, Integer status, int page, int size);

    long countByInviteeId(UUID inviteeId, Integer status);

    List<CourseInvitation> findByInviterId(UUID inviterId, int page, int size);

    long countByInviterId(UUID inviterId);
}
