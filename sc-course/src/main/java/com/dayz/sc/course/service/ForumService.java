package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.course.model.dto.*;
import com.dayz.sc.course.model.entity.Course;
import com.dayz.sc.course.model.entity.Enrollment;
import com.dayz.sc.course.model.entity.Forum;
import com.dayz.sc.course.model.entity.ForumPost;
import com.dayz.sc.course.model.entity.ForumReply;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.enums.ForumStatus;
import com.dayz.sc.course.model.enums.PostStatus;
import com.dayz.sc.course.model.vo.ForumPostVO;
import com.dayz.sc.course.model.vo.ForumReplyVO;
import com.dayz.sc.course.model.vo.ForumVO;
import com.dayz.sc.course.repository.CourseRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import com.dayz.sc.course.repository.ForumPostRepository;
import com.dayz.sc.course.repository.ForumReplyRepository;
import com.dayz.sc.course.repository.ForumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForumService {

    private static final int FLAG_OFF = 0;
    private static final int FLAG_ON = 1;
    private static final int DEFAULT_COMMENT_POST_TYPE = 0;
    private static final int COMMENT_TITLE_MAX_LENGTH = 40;
    private static final String FORUM_IMAGE_USAGE = "FORUM_IMAGE";
    private static final String COURSE_SCOPE_TYPE = "COURSE";
    private static final String READY_STATUS = "READY";
    private static final String DEFAULT_COMMENT_FORUM_NAME = "\u8bfe\u7a0b\u8bc4\u8bba\u533a";
    private static final String DEFAULT_COMMENT_TITLE = "\u8bfe\u7a0b\u8bc4\u8bba";

    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ForumRepository forumRepository;
    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;
    private final StorageInternalClient storageInternalClient;

    // ==================== Forum CRUD ====================

    @Transactional(rollbackFor = Exception.class)
    public UUID createForum(CreateForumRequest request, UUID userId) {
        Forum forum = new Forum();
        forum.setCourseId(request.courseId());
        forum.setForumName(request.forumName());
        forum.setDescription(request.description());
        forum.setForumType(request.forumType());
        forum.setTags(request.tags());
        forum.setPostCount(0L);
        forum.setReplyCount(0L);
        forum.setStatus(ForumStatus.NORMAL.getCode());
        forum.setId(UuidV7Generator.generate());

        forumRepository.save(forum);
        return forum.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateForum(UUID forumId, UpdateForumRequest request, UUID userId, Integer role) {
        Forum forum = forumRepository.findById(forumId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (request.forumName() != null) {
            forum.setForumName(request.forumName());
        }
        if (request.description() != null) {
            forum.setDescription(request.description());
        }
        if (request.forumType() != null) {
            forum.setForumType(request.forumType());
        }
        if (request.tags() != null) {
            forum.setTags(request.tags());
        }
        if (request.status() != null) {
            ForumStatus.fromCode(request.status());
            forum.setStatus(request.status());
        }

        forumRepository.update(forum);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteForum(UUID forumId, UUID userId, Integer role) {
        Forum forum = forumRepository.findById(forumId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        forumRepository.deleteById(forumId);
    }

    public ForumVO getForum(UUID forumId) {
        Forum forum = forumRepository.findById(forumId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        return toForumVO(forum);
    }

    public PageResponse<ForumVO> listForums(ForumPageRequest request) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        Page<Forum> result = forumRepository.findAll(page, size,
                request.courseId(), request.forumType(), request.status());

        List<ForumVO> voList = result.getRecords().stream().map(this::toForumVO).toList();
        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    public List<ForumVO> listForumsByCourse(UUID courseId) {
        return forumRepository.findByCourseId(courseId).stream()
                .map(this::toForumVO)
                .toList();
    }

    public PageResponse<ForumPostVO> listCourseComments(UUID courseId, Long pageValue, Long sizeValue) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        int page = PageUtils.normalizePage(pageValue);
        int size = PageUtils.normalizeSize(sizeValue);
        Optional<Forum> forum = forumRepository.findDefaultByCourseId(courseId);
        if (forum.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        Page<ForumPost> result = forumPostRepository.findAll(page, size,
                forum.get().getId(), courseId, PostStatus.NORMAL.getCode(), null);

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

        Forum forum = getOrCreateDefaultForum(courseId);
        validateCommentImages(courseId, request.imageUrls());
        ForumPost post = new ForumPost();
        post.setForumId(forum.getId());
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
        forum.setPostCount(forum.getPostCount() + 1);
        forumRepository.update(forum);

        return post.getId();
    }

    // ==================== Post CRUD ====================

    @Transactional(rollbackFor = Exception.class)
    public UUID createPost(CreateForumPostRequest request, UUID userId) {
        Forum forum = forumRepository.findById(request.forumId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        ForumPost post = new ForumPost();
        post.setForumId(request.forumId());
        post.setCourseId(forum.getCourseId());
        post.setSysUserId(userId);
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setPostType(request.postType());
        post.setAttachmentUrls(request.attachmentUrls());
        post.setImageUrls(request.imageUrls());
        post.setTags(request.tags());
        post.setChapterId(request.chapterId());
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

        // 更新论坛帖子计数
        forum.setPostCount(forum.getPostCount() + 1);
        forumRepository.update(forum);

        return post.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePost(UUID postId, UpdateForumPostRequest request, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!post.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        if (request.title() != null) {
            post.setTitle(request.title());
        }
        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.postType() != null) {
            post.setPostType(request.postType());
        }
        if (request.attachmentUrls() != null) {
            post.setAttachmentUrls(request.attachmentUrls());
        }
        if (request.imageUrls() != null) {
            post.setImageUrls(request.imageUrls());
        }
        if (request.tags() != null) {
            post.setTags(request.tags());
        }
        if (request.status() != null) {
            PostStatus.fromCode(request.status());
            post.setStatus(request.status());
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

        forumPostRepository.deleteById(postId);

        Forum forum = forumRepository.findById(post.getForumId()).orElse(null);
        if (forum != null && forum.getPostCount() > 0) {
            forum.setPostCount(forum.getPostCount() - 1);
            forumRepository.update(forum);
        }
    }

    public ForumPostVO getPost(UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        return toForumPostVO(post);
    }

    public PageResponse<ForumPostVO> listPosts(ForumPostPageRequest request) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        Page<ForumPost> result = forumPostRepository.findAll(page, size,
                request.forumId(), request.courseId(), request.status(), request.keyword());

        List<ForumPostVO> voList = result.getRecords().stream().map(this::toForumPostVO).toList();
        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    public List<ForumPostVO> listHotPosts(UUID courseId, int limit) {
        return forumPostRepository.findHotPosts(courseId, limit).stream()
                .map(this::toForumPostVO)
                .toList();
    }

    public List<ForumPostVO> listLatestPosts(UUID courseId, int limit) {
        return forumPostRepository.findLatestPosts(courseId, limit).stream()
                .map(this::toForumPostVO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleTop(UUID postId, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        post.setIsTop(post.getIsTop() == FLAG_OFF ? FLAG_ON : FLAG_OFF);
        forumPostRepository.update(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleEssence(UUID postId, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        post.setIsEssence(post.getIsEssence() == FLAG_OFF ? FLAG_ON : FLAG_OFF);
        forumPostRepository.update(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleLock(UUID postId, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        post.setIsLocked(post.getIsLocked() == FLAG_OFF ? FLAG_ON : FLAG_OFF);
        forumPostRepository.update(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void likePost(UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        post.setLikeCount(post.getLikeCount() + 1);
        forumPostRepository.update(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlikePost(UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        if (post.getLikeCount() > 0) {
            post.setLikeCount(post.getLikeCount() - 1);
            forumPostRepository.update(post);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void viewPost(UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        post.setViewCount(post.getViewCount() + 1);
        forumPostRepository.update(post);
    }

    // ==================== Reply CRUD ====================

    @Transactional(rollbackFor = Exception.class)
    public UUID createReply(CreateForumReplyRequest request, UUID userId, String ipAddress, String userAgent) {
        ForumPost post = forumPostRepository.findById(request.postId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (post.getIsLocked() == FLAG_ON) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "帖子已锁定，无法回复");
        }

        Forum forum = forumRepository.findById(post.getForumId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        int nextFloor = forumReplyRepository.findMaxFloorNumber(request.postId()) + 1;

        ForumReply reply = new ForumReply();
        reply.setPostId(request.postId());
        reply.setForumId(post.getForumId());
        reply.setCourseId(post.getCourseId());
        reply.setSysUserId(userId);
        reply.setContent(request.content());
        reply.setParentReplyId(request.parentReplyId());
        reply.setReplyToUserId(request.replyToUserId());
        reply.setAttachmentUrls(request.attachmentUrls());
        reply.setImageUrls(request.imageUrls());
        reply.setLikeCount(0L);
        reply.setReplyCount(0L);
        reply.setIsAccepted(FLAG_OFF);
        reply.setFloorNumber(nextFloor);
        reply.setStatus(FLAG_OFF);
        reply.setIpAddress(ipAddress);
        reply.setUserAgent(userAgent);
        reply.setId(UuidV7Generator.generate());

        forumReplyRepository.save(reply);

        // 更新帖子回复计数和最后回复信息
        post.setReplyCount(post.getReplyCount() + 1);
        post.setLastReplyId(reply.getId());
        post.setLastReplyTime(Instant.now());
        post.setLastReplyUserId(userId);
        forumPostRepository.update(post);

        // 更新论坛回复计数
        forum.setReplyCount(forum.getReplyCount() + 1);
        forumRepository.update(forum);

        return reply.getId();
    }

    public PageResponse<ForumReplyVO> listReplies(ForumReplyPageRequest request) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        Page<ForumReply> result = forumReplyRepository.findAll(page, size,
                request.postId(), request.forumId(), request.courseId(), request.status());

        List<ForumReplyVO> voList = result.getRecords().stream().map(this::toForumReplyVO).toList();
        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    public List<ForumReplyVO> getReplyTree(UUID postId) {
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

    public List<ForumReplyVO> getCourseCommentReplyTree(UUID postId) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        requireDefaultCommentPost(post);
        return getReplyTree(postId);
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
        requireDefaultCommentPost(post);
        requireCourseMember(post.getCourseId(), userId, role);
        validateCommentImages(post.getCourseId(), request.imageUrls());

        return createReply(new CreateForumReplyRequest(
                postId,
                request.content(),
                request.parentReplyId(),
                request.replyToUserId(),
                null,
                request.imageUrls()
        ), userId, ipAddress, userAgent);
    }

    @Transactional(rollbackFor = Exception.class)
    public void acceptReply(UUID replyId) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        reply.setIsAccepted(FLAG_ON);
        forumReplyRepository.update(reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unacceptReply(UUID replyId) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        reply.setIsAccepted(FLAG_OFF);
        forumReplyRepository.update(reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void likeReply(UUID replyId) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        reply.setLikeCount(reply.getLikeCount() + 1);
        forumReplyRepository.update(reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unlikeReply(UUID replyId) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        if (reply.getLikeCount() > 0) {
            reply.setLikeCount(reply.getLikeCount() - 1);
            forumReplyRepository.update(reply);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateReply(UUID replyId, UpdateForumReplyRequest request, UUID userId, Integer role) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!reply.getSysUserId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        reply.setContent(request.content());
        if (request.imageUrls() != null) {
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

        forumReplyRepository.deleteById(replyId);

        // 递减帖子回复计数
        ForumPost post = forumPostRepository.findById(reply.getPostId()).orElse(null);
        if (post != null && post.getReplyCount() > 0) {
            post.setReplyCount(post.getReplyCount() - 1);
            forumPostRepository.update(post);
        }

        // 递减论坛回复计数
        Forum forum = forumRepository.findById(reply.getForumId()).orElse(null);
        if (forum != null && forum.getReplyCount() > 0) {
            forum.setReplyCount(forum.getReplyCount() - 1);
            forumRepository.update(forum);
        }
    }

    // ==================== VO Converters ====================

    private ForumVO toForumVO(Forum forum) {
        return new ForumVO(
                forum.getId(),
                forum.getCourseId(),
                forum.getForumName(),
                forum.getDescription(),
                forum.getForumType(),
                forum.getPostCount(),
                forum.getReplyCount(),
                forum.getStatus(),
                forum.getTags(),
                forum.getCreatedAt(),
                forum.getUpdatedAt()
        );
    }

    private Forum getOrCreateDefaultForum(UUID courseId) {
        return forumRepository.findDefaultByCourseId(courseId)
                .orElseGet(() -> createDefaultForum(courseId));
    }

    private Forum createDefaultForum(UUID courseId) {
        Forum forum = new Forum();
        forum.setId(UuidV7Generator.generate());
        forum.setCourseId(courseId);
        forum.setForumName(DEFAULT_COMMENT_FORUM_NAME);
        forum.setForumType(0);
        forum.setPostCount(0L);
        forum.setReplyCount(0L);
        forum.setStatus(ForumStatus.NORMAL.getCode());
        forumRepository.save(forum);
        return forum;
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

    private void requireDefaultCommentPost(ForumPost post) {
        Forum forum = forumRepository.findDefaultByCourseId(post.getCourseId())
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        if (!forum.getId().equals(post.getForumId())) {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }
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

    private ForumPostVO toForumPostVO(ForumPost post) {
        return toForumPostVO(post, Map.of());
    }

    private ForumPostVO toForumPostVO(ForumPost post, Map<UUID, String> imageUrlMap) {
        return new ForumPostVO(
                post.getId(),
                post.getForumId(),
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

    private ForumReplyVO toForumReplyVO(ForumReply reply) {
        return toForumReplyVO(reply, Map.of());
    }

    private ForumReplyVO toForumReplyVO(ForumReply reply, Map<UUID, String> imageUrlMap) {
        return new ForumReplyVO(
                reply.getId(),
                reply.getPostId(),
                reply.getForumId(),
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
                reply.getForumId(),
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
