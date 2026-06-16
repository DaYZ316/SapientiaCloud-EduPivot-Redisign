package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.CreateCourseCommentReplyRequest;
import com.dayz.sc.course.model.dto.CreateCourseCommentRequest;
import com.dayz.sc.course.model.dto.UpdateForumPostRequest;
import com.dayz.sc.course.model.dto.UpdateForumReplyRequest;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.entity.ForumPost;
import com.dayz.sc.course.model.entity.ForumReply;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.enums.PostStatus;
import com.dayz.sc.course.model.vo.ForumPostVO;
import com.dayz.sc.course.model.vo.ForumReplyVO;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import com.dayz.sc.course.repository.ForumPostRepository;
import com.dayz.sc.course.repository.ForumReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Service
@RequiredArgsConstructor
public class ForumService {

    private static final int FLAG_OFF = 0;
    private static final int DEFAULT_COMMENT_POST_TYPE = 0;
    private static final int COMMENT_TITLE_MAX_LENGTH = 40;
    private static final String FORUM_IMAGE_USAGE = "FORUM_IMAGE";
    private static final String COURSE_SCOPE_TYPE = "COURSE";
    private static final String READY_STATUS = "READY";
    private static final String DEFAULT_COMMENT_TITLE = "课程评论";

    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final StorageInternalClient storageInternalClient;

    public PageResponse<ForumPostVO> listCourseComments(UUID courseId, Long pageValue, Long sizeValue) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        int page = PageUtils.normalizePage(pageValue);
        int size = PageUtils.normalizeSize(sizeValue);
        Page<ForumPost> result = forumPostRepository.findAll(page, size, courseId,
                PostStatus.NORMAL.getCode(), null);

        List<ForumPost> posts = result.getRecords();
        Map<UUID, String> imageUrls = loadImageUrlMap(posts.stream()
                .flatMap(post -> parseImageIds(post.getImageUrls()).stream())
                .distinct()
                .toList());
        List<ForumPostVO> voList = posts.stream().map(post -> toForumPostVO(post, imageUrls)).toList();
        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    @Transactional(rollbackFor = Exception.class)
    public UUID createCourseComment(UUID courseId, CreateCourseCommentRequest request, UUID userId, Integer role) {
        requireCourseMember(courseId, userId, role);
        validateCommentImages(courseId, request.imageUrls());

        ForumPost post = new ForumPost();
        post.setCourseId(courseId);
        post.setSysUserId(userId);
        post.setTitle(commentTitle(request.content()));
        post.setContent(request.content());
        post.setPostType(DEFAULT_COMMENT_POST_TYPE);
        post.setImageUrls(request.imageUrls());
        post.setViewCount(0L);
        post.setLikeCount(0L);
        post.setReplyCount(0L);
        post.setShareCount(0L);
        post.setIsTop(FLAG_OFF);
        post.setIsEssence(FLAG_OFF);
        post.setIsLocked(FLAG_OFF);
        post.setStatus(PostStatus.NORMAL.getCode());
        post.setId(UuidV7Generator.generate());

        forumPostRepository.save(post);
        return post.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePost(UUID postId, UpdateForumPostRequest request, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!post.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        requireCourseCommentPost(post);
        if (request.content() != null) {
            post.setContent(request.content());
            post.setTitle(commentTitle(request.content()));
        }
        if (request.imageUrls() != null) {
            validateCommentImages(post.getCourseId(), request.imageUrls());
            post.setImageUrls(request.imageUrls());
        }

        forumPostRepository.update(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePost(UUID postId, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!post.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        requireCourseCommentPost(post);
        forumPostRepository.deleteById(postId);
    }

    public List<ForumReplyVO> getCourseCommentReplyTree(UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireCourseCommentPost(post);

        List<ForumReply> allReplies = forumReplyRepository.findByPostId(postId, 1, 10000).getRecords();
        Map<UUID, List<ForumReply>> childrenMap = allReplies.stream()
                .filter(r -> r.getParentReplyId() != null)
                .collect(Collectors.groupingBy(ForumReply::getParentReplyId));
        Map<UUID, String> imageUrls = loadImageUrlMap(allReplies.stream()
                .flatMap(reply -> parseImageIds(reply.getImageUrls()).stream())
                .distinct()
                .toList());

        return allReplies.stream()
                .filter(r -> r.getParentReplyId() == null)
                .map(r -> toForumReplyVOWithChildren(r, childrenMap, imageUrls))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public UUID createCourseCommentReply(UUID postId,
                                         CreateCourseCommentReplyRequest request,
                                         UUID userId,
                                         Integer role,
                                         String ipAddress,
                                         String userAgent) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireCourseCommentPost(post);
        requireCourseMember(post.getCourseId(), userId, role);
        validateCommentImages(post.getCourseId(), request.imageUrls());

        if (post.getIsLocked() != null && post.getIsLocked() == 1) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "评论已锁定，无法回复");
        }

        int nextFloor = forumReplyRepository.findMaxFloorNumber(postId) + 1;

        ForumReply reply = new ForumReply();
        reply.setPostId(postId);
        reply.setCourseId(post.getCourseId());
        reply.setSysUserId(userId);
        reply.setContent(request.content());
        reply.setParentReplyId(request.parentReplyId());
        reply.setReplyToUserId(request.replyToUserId());
        reply.setImageUrls(request.imageUrls());
        reply.setLikeCount(0L);
        reply.setReplyCount(0L);
        reply.setIsAccepted(FLAG_OFF);
        reply.setFloorNumber(nextFloor);
        reply.setStatus(PostStatus.NORMAL.getCode());
        reply.setIpAddress(ipAddress);
        reply.setUserAgent(userAgent);
        reply.setId(UuidV7Generator.generate());

        forumReplyRepository.save(reply);

        post.setReplyCount(post.getReplyCount() + 1);
        post.setLastReplyId(reply.getId());
        post.setLastReplyTime(Instant.now());
        post.setLastReplyUserId(userId);
        forumPostRepository.update(post);

        return reply.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateReply(UUID replyId, UpdateForumReplyRequest request, UUID userId, Integer role) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!reply.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        requireCourseCommentReply(reply);
        reply.setContent(request.content());
        if (request.imageUrls() != null) {
            validateCommentImages(reply.getCourseId(), request.imageUrls());
            reply.setImageUrls(request.imageUrls());
        }

        forumReplyRepository.update(reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(UUID replyId, UUID userId, Integer role) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!reply.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        requireCourseCommentReply(reply);
        forumReplyRepository.deleteById(replyId);

        ForumPost post = forumPostRepository.findById(reply.getPostId()).orElse(null);
        if (post != null && post.getReplyCount() > 0) {
            post.setReplyCount(post.getReplyCount() - 1);
            forumPostRepository.update(post);
        }
    }

    private void requireCourseMember(UUID courseId, UUID userId, Integer role) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (SecurityUtils.isAdmin(role)
                || course.getTeacherId().equals(userId)
                || courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)
                || hasActiveEnrollment(courseId, userId)) {
            return;
        }

        throw new BusinessException(ErrorCodes.FORBIDDEN);
    }

    private void requireCourseCommentPost(ForumPost post) {
        if (post.getPostType() == null || post.getPostType() != DEFAULT_COMMENT_POST_TYPE) {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }
    }

    private void requireCourseCommentReply(ForumReply reply) {
        ForumPost post = forumPostRepository.findById(reply.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireCourseCommentPost(post);
    }

    private boolean hasActiveEnrollment(UUID courseId, UUID userId) {
        return enrollmentRepository.findByCourseIdAndStudentId(courseId, userId)
                .map(Enrollment::getStatus)
                .map(status -> status == EnrollmentStatus.ACTIVE.getCode()
                        || status == EnrollmentStatus.COMPLETED.getCode())
                .orElse(false);
    }

    private String commentTitle(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return DEFAULT_COMMENT_TITLE;
        }
        return normalized.length() <= COMMENT_TITLE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, COMMENT_TITLE_MAX_LENGTH);
    }

    private ForumPostVO toForumPostVO(ForumPost post, Map<UUID, String> imageUrlMap) {
        return new ForumPostVO(
                post.getId(),
                post.getCourseId(),
                post.getSysUserId(),
                post.getTitle(),
                post.getContent(),
                post.getPostType(),
                post.getAttachmentUrls(),
                resolveImageUrls(post.getImageUrls(), imageUrlMap),
                post.getTags(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getReplyCount(),
                post.getShareCount(),
                post.getIsTop(),
                post.getIsEssence(),
                post.getIsLocked(),
                post.getLastReplyId(),
                post.getLastReplyTime(),
                post.getLastReplyUserId(),
                post.getStatus(),
                post.getChapterId(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private ForumReplyVO toForumReplyVO(ForumReply reply, Map<UUID, String> imageUrlMap) {
        return new ForumReplyVO(
                reply.getId(),
                reply.getPostId(),
                reply.getCourseId(),
                reply.getSysUserId(),
                reply.getContent(),
                reply.getParentReplyId(),
                reply.getReplyToUserId(),
                reply.getAttachmentUrls(),
                resolveImageUrls(reply.getImageUrls(), imageUrlMap),
                reply.getLikeCount(),
                reply.getReplyCount(),
                reply.getIsAccepted(),
                reply.getFloorNumber(),
                reply.getStatus(),
                null,
                reply.getCreatedAt(),
                reply.getUpdatedAt()
        );
    }

    private ForumReplyVO toForumReplyVOWithChildren(ForumReply reply,
                                                    Map<UUID, List<ForumReply>> childrenMap,
                                                    Map<UUID, String> imageUrlMap) {
        List<ForumReply> childReplies = childrenMap.getOrDefault(reply.getId(), List.of());
        List<ForumReplyVO> children = childReplies.stream()
                .map(r -> toForumReplyVOWithChildren(r, childrenMap, imageUrlMap))
                .toList();

        return new ForumReplyVO(
                reply.getId(),
                reply.getPostId(),
                reply.getCourseId(),
                reply.getSysUserId(),
                reply.getContent(),
                reply.getParentReplyId(),
                reply.getReplyToUserId(),
                reply.getAttachmentUrls(),
                resolveImageUrls(reply.getImageUrls(), imageUrlMap),
                reply.getLikeCount(),
                reply.getReplyCount(),
                reply.getIsAccepted(),
                reply.getFloorNumber(),
                reply.getStatus(),
                children,
                reply.getCreatedAt(),
                reply.getUpdatedAt()
        );
    }

    private List<UUID> parseImageIds(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::parseUuidOrNull)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private void validateCommentImages(UUID courseId, List<String> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return;
        }

        for (String imageId : imageIds) {
            UUID fileId = parseUuidOrNull(imageId);
            if (fileId == null) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid comment image");
            }
            StorageObjectInfo file = requireStorageFile(fileId);
            if (!READY_STATUS.equals(file.status())
                    || !FORUM_IMAGE_USAGE.equals(file.usage())
                    || !COURSE_SCOPE_TYPE.equals(file.scopeType())
                    || !courseId.equals(file.scopeId())) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid comment image");
            }
        }
    }

    private StorageObjectInfo requireStorageFile(UUID fileId) {
        ApiResponse<StorageObjectInfo> response = storageInternalClient.getFile(fileId);
        if (response == null || response.code() != ErrorCodes.SUCCESS.code() || response.data() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid comment image");
        }
        return response.data();
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Map<UUID, String> loadImageUrlMap(List<UUID> imageIds) {
        if (imageIds.isEmpty()) {
            return Map.of();
        }
        ApiResponse<Map<UUID, String>> response = storageInternalClient.getUrls(imageIds);
        if (response == null || response.code() != ErrorCodes.SUCCESS.code() || response.data() == null) {
            return Map.of();
        }
        return response.data();
    }

    private List<String> resolveImageUrls(List<String> values, Map<UUID, String> imageUrlMap) {
        if (values == null || values.isEmpty()) {
            return values;
        }
        return values.stream()
                .map(value -> {
                    UUID imageId = parseUuidOrNull(value);
                    return imageId == null ? value : imageUrlMap.get(imageId);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
