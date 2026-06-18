package com.dayz.sc.course.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dayz.sc.course.mapper.LivePracticeSubmissionMapper;
import com.dayz.sc.course.model.entity.LivePracticeSubmission;
import com.dayz.sc.course.repository.LivePracticeSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MybatisLivePracticeSubmissionRepository implements LivePracticeSubmissionRepository {

    private final LivePracticeSubmissionMapper livePracticeSubmissionMapper;

    @Override
    public Optional<LivePracticeSubmission> findByGroupIdAndQuestionSnapshotIdAndStudentId(UUID groupId, UUID questionSnapshotId, UUID studentId) {
        LambdaQueryWrapper<LivePracticeSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeSubmission::getGroupId, groupId);
        wrapper.eq(LivePracticeSubmission::getQuestionSnapshotId, questionSnapshotId);
        wrapper.eq(LivePracticeSubmission::getStudentId, studentId);
        return Optional.ofNullable(livePracticeSubmissionMapper.selectOne(wrapper));
    }

    @Override
    public void save(LivePracticeSubmission submission) {
        livePracticeSubmissionMapper.insert(submission);
    }

    @Override
    public List<LivePracticeSubmission> findByGroupId(UUID groupId) {
        LambdaQueryWrapper<LivePracticeSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeSubmission::getGroupId, groupId);
        wrapper.orderByDesc(LivePracticeSubmission::getSubmittedAt);
        return livePracticeSubmissionMapper.selectList(wrapper);
    }

    @Override
    public List<LivePracticeSubmission> findByCourseIdAndStudentId(UUID courseId, UUID studentId) {
        LambdaQueryWrapper<LivePracticeSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LivePracticeSubmission::getCourseId, courseId);
        wrapper.eq(LivePracticeSubmission::getStudentId, studentId);
        wrapper.orderByDesc(LivePracticeSubmission::getSubmittedAt);
        return livePracticeSubmissionMapper.selectList(wrapper);
    }

    @Override
    public List<LivePracticeSubmission> findByGroupIds(List<UUID> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<LivePracticeSubmission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(LivePracticeSubmission::getGroupId, groupIds);
        wrapper.orderByDesc(LivePracticeSubmission::getSubmittedAt);
        return livePracticeSubmissionMapper.selectList(wrapper);
    }
}
