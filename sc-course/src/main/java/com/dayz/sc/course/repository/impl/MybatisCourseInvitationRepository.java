package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.CourseInvitationMapper;
import com.dayz.sc.course.model.entity.CourseInvitation;
import com.dayz.sc.course.model.enums.InvitationStatus;
import com.dayz.sc.course.repository.CourseInvitationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 数据访问接口。
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Repository
@RequiredArgsConstructor
public class MybatisCourseInvitationRepository implements CourseInvitationRepository {

    private final CourseInvitationMapper courseInvitationMapper;

    @Override
    public void save(CourseInvitation invitation) {
        courseInvitationMapper.insert(invitation);
    }

    @Override
    public void update(CourseInvitation invitation) {
        courseInvitationMapper.updateById(invitation);
    }

    @Override
    public Optional<CourseInvitation> findById(UUID id) {
        return Optional.ofNullable(courseInvitationMapper.selectById(id));
    }

    @Override
    public Optional<CourseInvitation> findPendingByCourseIdAndInviteeId(UUID courseId, UUID inviteeId) {
        LambdaQueryWrapper<CourseInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseInvitation::getCourseId, courseId)
                .eq(CourseInvitation::getInviteeId, inviteeId)
                .eq(CourseInvitation::getStatus, InvitationStatus.PENDING.getCode());
        return Optional.ofNullable(courseInvitationMapper.selectOne(wrapper));
    }

    @Override
    public boolean existsPendingByCourseIdAndInviteeId(UUID courseId, UUID inviteeId) {
        LambdaQueryWrapper<CourseInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseInvitation::getCourseId, courseId)
                .eq(CourseInvitation::getInviteeId, inviteeId)
                .eq(CourseInvitation::getStatus, InvitationStatus.PENDING.getCode());
        return courseInvitationMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<CourseInvitation> findByInviteeId(UUID inviteeId, Integer status, int page, int size) {
        LambdaQueryWrapper<CourseInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseInvitation::getInviteeId, inviteeId);
        if (status != null) {
            wrapper.eq(CourseInvitation::getStatus, status);
        }
        wrapper.orderByDesc(CourseInvitation::getCreatedAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return courseInvitationMapper.selectList(wrapper);
    }

    @Override
    public long countByInviteeId(UUID inviteeId, Integer status) {
        LambdaQueryWrapper<CourseInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseInvitation::getInviteeId, inviteeId);
        if (status != null) {
            wrapper.eq(CourseInvitation::getStatus, status);
        }
        return courseInvitationMapper.selectCount(wrapper);
    }

    @Override
    public List<CourseInvitation> findByInviterId(UUID inviterId, int page, int size) {
        LambdaQueryWrapper<CourseInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseInvitation::getInviterId, inviterId);
        wrapper.orderByDesc(CourseInvitation::getCreatedAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        return courseInvitationMapper.selectList(wrapper);
    }

    @Override
    public long countByInviterId(UUID inviterId) {
        LambdaQueryWrapper<CourseInvitation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseInvitation::getInviterId, inviterId);
        return courseInvitationMapper.selectCount(wrapper);
    }
}
