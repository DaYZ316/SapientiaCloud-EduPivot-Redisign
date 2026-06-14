package com.dayz.sc.course.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.support.SecurityUtils;
import com.dayz.sc.common.util.PageUtils;
import com.dayz.sc.common.util.UuidV7Generator;
import com.dayz.sc.course.model.dto.CreateChapterRequest;
import com.dayz.sc.course.model.dto.ChapterPageRequest;
import com.dayz.sc.course.model.dto.UpdateChapterRequest;
import com.dayz.sc.course.model.entity.Chapter;
import com.dayz.sc.course.model.enums.ChapterStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.ChapterInteractionVO;
import com.dayz.sc.course.model.vo.ChapterVO;
import com.dayz.sc.course.repository.ChapterLikeRepository;
import com.dayz.sc.course.repository.ChapterRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final ChapterLikeRepository chapterLikeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final Duration VIEW_DEDUPLICATE_TTL = Duration.ofMinutes(30);
    private static final String VIEW_KEY_PREFIX = "course:chapter:view:";

    @Transactional(rollbackFor = Exception.class)
    public UUID createChapter(CreateChapterRequest request, UUID teacherId) {
        if (chapterRepository.existsByCourseIdAndChapterName(request.courseId(), request.chapterName())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "章节名称已存在");
        }

        Chapter chapter = new Chapter();
        chapter.setCourseId(request.courseId());
        chapter.setTeacherId(teacherId);
        chapter.setChapterName(request.chapterName());
        chapter.setParentChapterId(request.parentChapterId());
        chapter.setDescription(request.description());
        chapter.setContent(request.content());
        chapter.setAttachmentUrls(request.attachmentUrls());
        chapter.setSortOrder(request.sortOrder());
        chapter.setStatus(ChapterStatus.DRAFT.getCode());
        chapter.setViewCount(0L);
        chapter.setLikeCount(0L);
        chapter.setId(UuidV7Generator.generate());

        chapterRepository.save(chapter);
        return chapter.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateChapter(UUID chapterId, UpdateChapterRequest request, UUID userId, Integer role) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!chapter.getTeacherId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        if (request.chapterName() != null) {
            chapter.setChapterName(request.chapterName());
        }
        if (request.parentChapterId() != null) {
            chapter.setParentChapterId(request.parentChapterId());
        }
        if (request.description() != null) {
            chapter.setDescription(request.description());
        }
        if (request.content() != null) {
            chapter.setContent(request.content());
        }
        if (request.attachmentUrls() != null) {
            chapter.setAttachmentUrls(request.attachmentUrls());
        }
        if (request.sortOrder() != null) {
            chapter.setSortOrder(request.sortOrder());
        }
        if (request.status() != null) {
            ChapterStatus.fromCode(request.status());
            chapter.setStatus(request.status());
        }

        chapterRepository.update(chapter);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(UUID chapterId, UUID userId, Integer role) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));

        if (!chapter.getTeacherId().equals(userId) && !SecurityUtils.isAdmin(role)) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }

        chapterRepository.deleteById(chapterId);
    }

    public ChapterVO getChapter(UUID chapterId, @Nullable UUID userId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        boolean likedByMe = userId != null && chapterLikeRepository.existsByChapterIdAndUserId(chapterId, userId);
        return toChapterVO(chapter, null, likedByMe);
    }

    public PageResponse<ChapterVO> listChapters(ChapterPageRequest request) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        Page<Chapter> result = chapterRepository.findAll(page, size,
                request.courseId(), request.status(), request.keyword());

        List<ChapterVO> voList = result.getRecords().stream()
                .map(ch -> toChapterVO(ch, null, false))
                .toList();

        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    public List<ChapterVO> listChaptersByCourse(UUID courseId, @Nullable UUID userId) {
        List<Chapter> chapters = chapterRepository.findByCourseId(courseId);
        return buildChapterTree(chapters, userId);
    }

    public List<ChapterVO> getChapterTree(UUID courseId, @Nullable UUID userId) {
        List<Chapter> allChapters = chapterRepository.findByCourseId(courseId);
        return buildChapterTree(allChapters, userId);
    }

    public ChapterInteractionVO viewChapter(UUID chapterId, UUID userId, Integer role) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        checkCourseAccess(chapter.getCourseId(), userId, role);

        try {
            String key = VIEW_KEY_PREFIX + chapterId + ":" + userId;
            Boolean added = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", VIEW_DEDUPLICATE_TTL);
            if (Boolean.TRUE.equals(added)) {
                chapterRepository.incrementViewCount(chapterId);
            }
        } catch (Exception e) {
            log.warn("Redis 浏览去重异常，降级为本次计入浏览: chapterId={}", chapterId, e);
            chapterRepository.incrementViewCount(chapterId);
        }

        Chapter updated = chapterRepository.findById(chapterId).orElse(chapter);
        boolean likedByMe = chapterLikeRepository.existsByChapterIdAndUserId(chapterId, userId);
        return new ChapterInteractionVO(chapterId, updated.getViewCount(), updated.getLikeCount(), likedByMe);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChapterInteractionVO likeChapter(UUID chapterId, UUID userId, Integer role) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        checkCourseAccess(chapter.getCourseId(), userId, role);

        boolean inserted = chapterLikeRepository.like(chapterId, chapter.getCourseId(), userId);
        if (inserted) {
            chapterRepository.incrementLikeCount(chapterId);
        }

        Chapter updated = chapterRepository.findById(chapterId).orElse(chapter);
        return new ChapterInteractionVO(chapterId, updated.getViewCount(), updated.getLikeCount(), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChapterInteractionVO unlikeChapter(UUID chapterId, UUID userId, Integer role) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        checkCourseAccess(chapter.getCourseId(), userId, role);

        boolean removed = chapterLikeRepository.unlike(chapterId, userId);
        if (removed) {
            chapterRepository.decrementLikeCount(chapterId);
        }

        Chapter updated = chapterRepository.findById(chapterId).orElse(chapter);
        return new ChapterInteractionVO(chapterId, updated.getViewCount(), updated.getLikeCount(), false);
    }

    private void checkCourseAccess(UUID courseId, UUID userId, Integer role) {
        if (SecurityUtils.isAdmin(role)) {
            return;
        }
        if (SecurityUtils.isTeacher(role) && courseTeacherRepository.existsByCourseIdAndTeacherId(courseId, userId)) {
            return;
        }
        enrollmentRepository.findByCourseIdAndStudentId(courseId, userId)
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE.getCode()
                        || e.getStatus() == EnrollmentStatus.COMPLETED.getCode())
                .orElseThrow(() -> new BusinessException(ErrorCodes.FORBIDDEN, "无课程访问权限"));
    }

    private List<ChapterVO> buildChapterTree(List<Chapter> allChapters, @Nullable UUID userId) {
        Map<UUID, List<Chapter>> childrenMap = allChapters.stream()
                .filter(ch -> ch.getParentChapterId() != null)
                .collect(Collectors.groupingBy(Chapter::getParentChapterId));

        Set<UUID> likedChapterIds = Set.of();
        if (userId != null) {
            List<UUID> allIds = allChapters.stream().map(Chapter::getId).toList();
            likedChapterIds = chapterLikeRepository.findLikedChapterIds(userId, allIds);
        }

        Set<UUID> finalLikedChapterIds = likedChapterIds;
        return allChapters.stream()
                .filter(ch -> ch.getParentChapterId() == null)
                .map(ch -> toChapterVO(ch, childrenMap, finalLikedChapterIds))
                .toList();
    }

    private ChapterVO toChapterVO(Chapter chapter, Map<UUID, List<Chapter>> childrenMap, Set<UUID> likedChapterIds) {
        return toChapterVO(chapter, childrenMap, likedChapterIds.contains(chapter.getId()), likedChapterIds);
    }

    private ChapterVO toChapterVO(Chapter chapter, @Nullable Map<UUID, List<Chapter>> childrenMap, boolean likedByMe) {
        return toChapterVO(chapter, childrenMap, likedByMe, Set.of());
    }

    private ChapterVO toChapterVO(Chapter chapter,
                                  @Nullable Map<UUID, List<Chapter>> childrenMap,
                                  boolean likedByMe,
                                  Set<UUID> likedChapterIds) {
        List<ChapterVO> children = null;
        if (childrenMap != null) {
            List<Chapter> childChapters = childrenMap.getOrDefault(chapter.getId(), List.of());
            children = childChapters.stream()
                    .map(ch -> toChapterVO(ch, childrenMap, likedChapterIds))
                    .toList();
        }

        return new ChapterVO(
                chapter.getId(),
                chapter.getCourseId(),
                chapter.getTeacherId(),
                chapter.getChapterName(),
                chapter.getParentChapterId(),
                chapter.getDescription(),
                chapter.getContent(),
                chapter.getAttachmentUrls(),
                chapter.getSortOrder(),
                chapter.getStatus(),
                chapter.getViewCount(),
                chapter.getLikeCount(),
                likedByMe,
                children,
                chapter.getCreatedAt(),
                chapter.getUpdatedAt()
        );
    }
}
