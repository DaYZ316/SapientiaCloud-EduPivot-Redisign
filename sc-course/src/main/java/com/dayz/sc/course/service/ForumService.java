package com.dayz.sc.course.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.*;
import com.dayz.sc.course.model.entity.Forum;
import com.dayz.sc.course.model.entity.ForumPost;
import com.dayz.sc.course.model.entity.ForumReply;
import com.dayz.sc.course.model.enums.ForumStatus;
import com.dayz.sc.course.model.enums.PostStatus;
import com.dayz.sc.course.model.vo.ForumPostVO;
import com.dayz.sc.course.model.vo.ForumReplyVO;
import com.dayz.sc.course.model.vo.ForumVO;
import com.dayz.sc.course.repository.ForumPostRepository;
import com.dayz.sc.course.repository.ForumReplyRepository;
import com.dayz.sc.course.repository.ForumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ForumService {

    private final ForumRepository forumRepository;
    private final ForumPostRepository forumPostRepository;
    private final ForumReplyRepository forumReplyRepository;

    // ==================== Forum CRUD ====================

    @Transactional(rollbackFor = Exception.class)
    public UUID createForum(CreateForumRequest request, UUID userId) {
        Forum forum = new Forum();
        forum.setCourseId(request.courseId());
        forum.setForumName(request.forumName());
        forum.setDescription(request.description());
        forum.setForumType(request.forumType());
        forum.setAllowAnonymous(request.allowAnonymous());
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
        if (request.allowAnonymous() != null) {
            forum.setAllowAnonymous(request.allowAnonymous());
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

        List<Forum> forums = forumRepository.findAll(page, size,
                request.courseId(), request.forumType(), request.status());
        long total = forumRepository.countAll(request.courseId(), request.forumType(), request.status());

        List<ForumVO> voList = forums.stream().map(this::toForumVO).toList();
        return new PageResponse<>(voList, total, page, size);
    }

    public List<ForumVO> listForumsByCourse(UUID courseId) {
        return forumRepository.findByCourseId(courseId).stream()
                .map(this::toForumVO)
                .toList();
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
        post.setIsAnonymous(request.isAnonymous());
        post.setAttachmentUrls(request.attachmentUrls());
        post.setImageUrls(request.imageUrls());
        post.setTags(request.tags());
        post.setChapterId(request.chapterId());
        post.setViewCount(0L);
        post.setLikeCount(0L);
        post.setReplyCount(0L);
        post.setShareCount(0L);
        post.setIsTop(0);
        post.setIsEssence(0);
        post.setIsLocked(0);
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
        if (request.isAnonymous() != null) {
            post.setIsAnonymous(request.isAnonymous());
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

        List<ForumPost> posts = forumPostRepository.findAll(page, size,
                request.forumId(), request.courseId(), request.status(), request.keyword());
        long total = forumPostRepository.countAll(request.forumId(), request.courseId(),
                request.status(), request.keyword());

        List<ForumPostVO> voList = posts.stream().map(this::toForumPostVO).toList();
        return new PageResponse<>(voList, total, page, size);
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
        post.setIsTop(post.getIsTop() == 0 ? 1 : 0);
        forumPostRepository.update(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleEssence(UUID postId, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        post.setIsEssence(post.getIsEssence() == 0 ? 1 : 0);
        forumPostRepository.update(post);
    }

    @Transactional(rollbackFor = Exception.class)
    public void toggleLock(UUID postId, UUID userId, Integer role) {
        ForumPost post = forumPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        post.setIsLocked(post.getIsLocked() == 0 ? 1 : 0);
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

        if (post.getIsLocked() == 1) {
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
        reply.setIsAnonymous(request.isAnonymous());
        reply.setAttachmentUrls(request.attachmentUrls());
        reply.setImageUrls(request.imageUrls());
        reply.setLikeCount(0L);
        reply.setReplyCount(0L);
        reply.setIsAccepted(0);
        reply.setFloorNumber(nextFloor);
        reply.setStatus(0);
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

        List<ForumReply> replies = forumReplyRepository.findAll(page, size,
                request.postId(), request.forumId(), request.courseId(), request.status());
        long total = forumReplyRepository.countAll(request.postId(), request.forumId(),
                request.courseId(), request.status());

        List<ForumReplyVO> voList = replies.stream().map(this::toForumReplyVO).toList();
        return new PageResponse<>(voList, total, page, size);
    }

    public List<ForumReplyVO> getReplyTree(UUID postId) {
        List<ForumReply> allReplies = forumReplyRepository.findByPostId(postId, 1, 10000);
        Map<UUID, List<ForumReply>> childrenMap = allReplies.stream()
                .filter(r -> r.getParentReplyId() != null)
                .collect(Collectors.groupingBy(ForumReply::getParentReplyId));

        return allReplies.stream()
                .filter(r -> r.getParentReplyId() == null)
                .map(r -> toForumReplyVOWithChildren(r, childrenMap))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void acceptReply(UUID replyId) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        reply.setIsAccepted(1);
        forumReplyRepository.update(reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unacceptReply(UUID replyId) {
        ForumReply reply = forumReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        reply.setIsAccepted(0);
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

    // ==================== VO Converters ====================

    private ForumVO toForumVO(Forum forum) {
        return new ForumVO(
                forum.getId(),
                forum.getCourseId(),
                forum.getForumName(),
                forum.getDescription(),
                forum.getForumType(),
                forum.getAllowAnonymous(),
                forum.getPostCount(),
                forum.getReplyCount(),
                forum.getStatus(),
                forum.getTags(),
                forum.getCreatedAt(),
                forum.getUpdatedAt()
        );
    }

    private ForumPostVO toForumPostVO(ForumPost post) {
        return new ForumPostVO(
                post.getId(),
                post.getForumId(),
                post.getCourseId(),
                post.getSysUserId(),
                post.getTitle(),
                post.getContent(),
                post.getPostType(),
                post.getIsAnonymous(),
                post.getAttachmentUrls(),
                post.getImageUrls(),
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
        return new ForumReplyVO(
                reply.getId(),
                reply.getPostId(),
                reply.getForumId(),
                reply.getCourseId(),
                reply.getSysUserId(),
                reply.getContent(),
                reply.getParentReplyId(),
                reply.getReplyToUserId(),
                reply.getIsAnonymous(),
                reply.getAttachmentUrls(),
                reply.getImageUrls(),
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

    private ForumReplyVO toForumReplyVOWithChildren(ForumReply reply, Map<UUID, List<ForumReply>> childrenMap) {
        List<ForumReply> childReplies = childrenMap.getOrDefault(reply.getId(), List.of());
        List<ForumReplyVO> children = childReplies.stream()
                .map(r -> toForumReplyVOWithChildren(r, childrenMap))
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
                reply.getIsAnonymous(),
                reply.getAttachmentUrls(),
                reply.getImageUrls(),
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
}
