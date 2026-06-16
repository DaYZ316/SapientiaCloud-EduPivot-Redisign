package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.UserBasicInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.event.CourseEventPublisher;
import com.dayz.sc.course.model.dto.InvitationPageRequest;
import com.dayz.sc.course.model.dto.InviteAssistantRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.CourseInvitation;
import com.dayz.sc.course.model.enums.InvitationStatus;
import com.dayz.sc.course.model.vo.CourseInvitationVO;
import com.dayz.sc.course.repository.CourseInvitationRepository;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final CourseInvitationRepository invitationRepository;
    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final AuthInternalClient authInternalClient;
    private final StorageInternalClient storageInternalClient;
    private final CourseEventPublisher courseEventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public UUID invite(InviteAssistantRequest request, UUID inviterId, Integer inviterRole) {
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        // 教师必须是课程主讲教师，管理员可邀请任意课程
        if (com.dayz.sc.common.security.support.SecurityUtils.isTeacher(inviterRole) && !course.getTeacherId().equals(inviterId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN, "只有主讲教师能邀请助教");
        }

        // 不能邀请自己
        if (inviterId.equals(request.inviteeId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "不能邀请自己作为助教");
        }

        // 验证被邀请人存在
        UserBasicInfo inviteeInfo = getBasicUserInfo(request.inviteeId());
        if (inviteeInfo == null) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "被邀请的教师不存在");
        }

        // 验证被邀请人不是课程已有教师
        if (courseTeacherRepository.existsByCourseIdAndTeacherId(request.courseId(), request.inviteeId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "该教师已经是课程教师");
        }

        // 验证没有待处理的重复邀请
        if (invitationRepository.existsPendingByCourseIdAndInviteeId(request.courseId(), request.inviteeId())) {
            throw new BusinessException(ErrorCodes.INVITATION_ALREADY_INVITED, "已存在待处理的邀请");
        }

        CourseInvitation invitation = new CourseInvitation();
        invitation.setId(UuidV7Generator.generate());
        invitation.setCourseId(request.courseId());
        invitation.setInviterId(inviterId);
        invitation.setInviteeId(request.inviteeId());

        // 管理员邀请直接接受，教师邀请需要对方确认
        if (com.dayz.sc.common.security.support.SecurityUtils.isAdmin(inviterRole)) {
            invitation.setStatus(InvitationStatus.ACCEPTED.getCode());
        } else {
            invitation.setStatus(InvitationStatus.PENDING.getCode());
        }
        invitation.setMessage(request.message());

        invitationRepository.save(invitation);

        // 管理员邀请直接加入课程教师关联表
        if (com.dayz.sc.common.security.support.SecurityUtils.isAdmin(inviterRole)) {
            courseTeacherRepository.batchSave(request.courseId(), List.of(request.inviteeId()));
        }

        // 获取邀请人信息用于通知
        UserBasicInfo inviterInfo = getBasicUserInfo(inviterId);
        String inviterName = inviterInfo != null ? inviterInfo.displayName() : "未知教师";
        String inviteeName = inviteeInfo.displayName() != null ? inviteeInfo.displayName() : "未知教师";

        String action = com.dayz.sc.common.security.support.SecurityUtils.isAdmin(inviterRole) ? "AUTO_ACCEPTED" : "INVITED";
        courseEventPublisher.publishInvitationChanged(
                request.courseId(), course.getTitle(),
                inviterId, inviterName,
                request.inviteeId(), inviteeName,
                action);

        if (com.dayz.sc.common.security.support.SecurityUtils.isAdmin(inviterRole)) {
            log.info("Admin {} auto-added {} as assistant for course {}", inviterId, request.inviteeId(), request.courseId());
        } else {
            log.info("Teacher {} invited {} as assistant for course {}", inviterId, request.inviteeId(), request.courseId());
        }
        return invitation.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void accept(UUID invitationId, UUID inviteeId) {
        CourseInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (invitation.getStatus() != InvitationStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCodes.INVITATION_NOT_PENDING, "邀请不在待处理状态");
        }

        if (!invitation.getInviteeId().equals(inviteeId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        // 更新邀请状态
        invitation.setStatus(InvitationStatus.ACCEPTED.getCode());
        invitationRepository.update(invitation);

        // 将助教加入课程教师关联表
        courseTeacherRepository.batchSave(invitation.getCourseId(), List.of(inviteeId));

        // 获取课程信息并发布事件
        Course course = courseRepository.findById(invitation.getCourseId()).orElse(null);
        String courseTitle = course != null ? course.getTitle() : "未知课程";

        UserBasicInfo inviterInfo = getBasicUserInfo(invitation.getInviterId());
        UserBasicInfo inviteeInfo = getBasicUserInfo(inviteeId);
        String inviterName = inviterInfo != null ? inviterInfo.displayName() : "未知教师";
        String inviteeName = inviteeInfo != null ? inviteeInfo.displayName() : "未知教师";

        courseEventPublisher.publishInvitationChanged(
                invitation.getCourseId(), courseTitle,
                invitation.getInviterId(), inviterName,
                inviteeId, inviteeName,
                "ACCEPTED");

        log.info("Teacher {} accepted invitation {} for course {}", inviteeId, invitationId, invitation.getCourseId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void decline(UUID invitationId, UUID inviteeId) {
        CourseInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (invitation.getStatus() != InvitationStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCodes.INVITATION_NOT_PENDING, "邀请不在待处理状态");
        }

        if (!invitation.getInviteeId().equals(inviteeId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        invitation.setStatus(InvitationStatus.DECLINED.getCode());
        invitationRepository.update(invitation);

        Course course = courseRepository.findById(invitation.getCourseId()).orElse(null);
        String courseTitle = course != null ? course.getTitle() : "未知课程";

        UserBasicInfo inviterInfo = getBasicUserInfo(invitation.getInviterId());
        UserBasicInfo inviteeInfo = getBasicUserInfo(inviteeId);
        String inviterName = inviterInfo != null ? inviterInfo.displayName() : "未知教师";
        String inviteeName = inviteeInfo != null ? inviteeInfo.displayName() : "未知教师";

        courseEventPublisher.publishInvitationChanged(
                invitation.getCourseId(), courseTitle,
                invitation.getInviterId(), inviterName,
                inviteeId, inviteeName,
                "DECLINED");

        log.info("Teacher {} declined invitation {} for course {}", inviteeId, invitationId, invitation.getCourseId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(UUID invitationId, UUID inviterId) {
        CourseInvitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (invitation.getStatus() != InvitationStatus.PENDING.getCode()) {
            throw new BusinessException(ErrorCodes.INVITATION_NOT_PENDING, "邀请不在待处理状态");
        }

        if (!invitation.getInviterId().equals(inviterId)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        invitation.setStatus(InvitationStatus.WITHDRAWN.getCode());
        invitationRepository.update(invitation);

        log.info("Teacher {} withdrew invitation {} for course {}", inviterId, invitationId, invitation.getCourseId());
    }

    public PageResponse<CourseInvitationVO> listReceived(InvitationPageRequest request, UUID inviteeId) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        List<CourseInvitation> invitations = invitationRepository.findByInviteeId(inviteeId, request.status(), page, size);
        long total = invitationRepository.countByInviteeId(inviteeId, request.status());

        List<CourseInvitationVO> voList = toInvitationVos(invitations);

        return new PageResponse<>(voList, total, page, size);
    }

    public PageResponse<CourseInvitationVO> listSent(InvitationPageRequest request, UUID inviterId) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        List<CourseInvitation> invitations = invitationRepository.findByInviterId(inviterId, page, size);
        long total = invitationRepository.countByInviterId(inviterId);

        List<CourseInvitationVO> voList = toInvitationVos(invitations);

        return new PageResponse<>(voList, total, page, size);
    }

    /**
     * 批量构建邀请 VO，避免 N+1 查询。
     */
    private List<CourseInvitationVO> toInvitationVos(List<CourseInvitation> invitations) {
        if (invitations.isEmpty()) {
            return List.of();
        }

        // 1. 批量查询课程
        List<UUID> courseIds = invitations.stream().map(CourseInvitation::getCourseId).distinct().toList();
        Map<UUID, Course> courseMap = courseRepository.findByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        // 2. 批量查询用户信息
        List<UUID> allUserIds = invitations.stream()
                .flatMap(inv -> java.util.stream.Stream.of(inv.getInviterId(), inv.getInviteeId()))
                .distinct().toList();
        Map<UUID, UserBasicInfo> userMap = getBasicUserInfoBatch(allUserIds);

        // 3. 批量查询封面 URL
        List<UUID> coverFileIds = courseMap.values().stream()
                .map(Course::getCoverFileId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> coverUrlMap = coverFileIds.isEmpty() ? Map.of() : resolveCoverUrlsBatch(coverFileIds);

        // 4. 组装 VO
        return invitations.stream().map(invitation -> {
            Course course = courseMap.get(invitation.getCourseId());
            String courseTitle = course != null ? course.getTitle() : null;
            String courseCoverUrl = null;
            if (course != null) {
                courseCoverUrl = course.getCoverFileId() != null
                        ? coverUrlMap.get(course.getCoverFileId())
                        : course.getCoverUrl();
            }

            UserBasicInfo inviterInfo = userMap.get(invitation.getInviterId());
            UserBasicInfo inviteeInfo = userMap.get(invitation.getInviteeId());

            return new CourseInvitationVO(
                    invitation.getId(),
                    invitation.getCourseId(),
                    courseTitle,
                    courseCoverUrl,
                    invitation.getInviterId(),
                    inviterInfo != null ? inviterInfo.displayName() : null,
                    inviterInfo != null ? inviterInfo.avatarUrl() : null,
                    invitation.getInviteeId(),
                    inviteeInfo != null ? inviteeInfo.displayName() : null,
                    invitation.getStatus(),
                    invitation.getMessage(),
                    invitation.getCreatedAt()
            );
        }).toList();
    }

    /**
     * 批量获取用户基本信息。
     */
    private Map<UUID, UserBasicInfo> getBasicUserInfoBatch(List<UUID> userIds) {
        try {
            ApiResponse<List<UserBasicInfo>> response = authInternalClient.getUsersBasicInfo(userIds);
            if (response != null && response.code() == 0 && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(UserBasicInfo::id, u -> u, (a, b) -> a));
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private String resolveCoverUrl(Course course) {
        if (course.getCoverFileId() != null) {
            try {
                ApiResponse<Map<UUID, String>> response = storageInternalClient.getUrls(List.of(course.getCoverFileId()));
                if (response != null && response.code() == 0 && response.data() != null) {
                    return response.data().get(course.getCoverFileId());
                }
            } catch (Exception ignored) {
            }
        }
        return course.getCoverUrl();
    }

    /**
     * 批量解析封面 URL。
     */
    private Map<UUID, String> resolveCoverUrlsBatch(List<UUID> fileIds) {
        try {
            ApiResponse<Map<UUID, String>> response = storageInternalClient.getUrls(fileIds);
            if (response != null && response.code() == 0 && response.data() != null) {
                return response.data();
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private UserBasicInfo getBasicUserInfo(UUID userId) {
        return getBasicUserInfoBatch(List.of(userId)).get(userId);
    }
}
