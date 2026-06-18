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

    /**
     * 保存课程邀请
     *
     * @param invitation 邀请实体
     */
    void save(CourseInvitation invitation);

    /**
     * 更新课程邀请
     *
     * @param invitation 邀请实体
     */
    void update(CourseInvitation invitation);

    /**
     * 根据ID查询课程邀请
     *
     * @param id 邀请ID
     * @return 邀请实体，可能为空
     */
    Optional<CourseInvitation> findById(UUID id);

    /**
     * 根据课程ID和被邀请人ID查询待处理的邀请
     *
     * @param courseId  课程ID
     * @param inviteeId 被邀请人ID
     * @return 邀请实体，可能为空
     */
    Optional<CourseInvitation> findPendingByCourseIdAndInviteeId(UUID courseId, UUID inviteeId);

    /**
     * 检查是否存在待处理的邀请
     *
     * @param courseId  课程ID
     * @param inviteeId 被邀请人ID
     * @return 是否存在
     */
    boolean existsPendingByCourseIdAndInviteeId(UUID courseId, UUID inviteeId);

    /**
     * 根据被邀请人ID分页查询邀请
     *
     * @param inviteeId 被邀请人ID
     * @param status    邀请状态
     * @param page      页码
     * @param size      每页大小
     * @return 邀请列表
     */
    List<CourseInvitation> findByInviteeId(UUID inviteeId, Integer status, int page, int size);

    /**
     * 统计被邀请人的邀请数量
     *
     * @param inviteeId 被邀请人ID
     * @param status    邀请状态
     * @return 邀请数量
     */
    long countByInviteeId(UUID inviteeId, Integer status);

    /**
     * 根据邀请人ID分页查询邀请
     *
     * @param inviterId 邀请人ID
     * @param page      页码
     * @param size      每页大小
     * @return 邀请列表
     */
    List<CourseInvitation> findByInviterId(UUID inviterId, int page, int size);

    /**
     * 统计邀请人的邀请数量
     *
     * @param inviterId 邀请人ID
     * @return 邀请数量
     */
    long countByInviterId(UUID inviterId);
}
