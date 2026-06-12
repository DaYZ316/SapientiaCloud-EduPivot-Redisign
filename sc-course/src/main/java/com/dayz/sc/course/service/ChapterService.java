package com.dayz.sc.course.service;

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
import com.dayz.sc.course.model.vo.ChapterVO;
import com.dayz.sc.course.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;

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

    public ChapterVO getChapter(UUID chapterId) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        return toChapterVO(chapter, null);
    }

    public PageResponse<ChapterVO> listChapters(ChapterPageRequest request) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        List<Chapter> chapters = chapterRepository.findAll(page, size,
                request.courseId(), request.status(), request.keyword());
        long total = chapterRepository.countAll(request.courseId(), request.status(), request.keyword());

        List<ChapterVO> voList = chapters.stream()
                .map(ch -> toChapterVO(ch, null))
                .toList();

        return new PageResponse<>(voList, total, page, size);
    }

    public List<ChapterVO> listChaptersByCourse(UUID courseId) {
        List<Chapter> chapters = chapterRepository.findByCourseId(courseId);
        return buildChapterTree(chapters);
    }

    public List<ChapterVO> getChapterTree(UUID courseId) {
        List<Chapter> allChapters = chapterRepository.findByCourseId(courseId);
        return buildChapterTree(allChapters);
    }

    private List<ChapterVO> buildChapterTree(List<Chapter> allChapters) {
        Map<UUID, List<Chapter>> childrenMap = allChapters.stream()
                .filter(ch -> ch.getParentChapterId() != null)
                .collect(Collectors.groupingBy(Chapter::getParentChapterId));

        return allChapters.stream()
                .filter(ch -> ch.getParentChapterId() == null)
                .map(ch -> toChapterVO(ch, childrenMap))
                .toList();
    }

    private ChapterVO toChapterVO(Chapter chapter, Map<UUID, List<Chapter>> childrenMap) {
        List<ChapterVO> children = null;
        if (childrenMap != null) {
            List<Chapter> childChapters = childrenMap.getOrDefault(chapter.getId(), List.of());
            children = childChapters.stream()
                    .map(ch -> toChapterVO(ch, childrenMap))
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
                children,
                chapter.getCreatedAt(),
                chapter.getUpdatedAt()
        );
    }
}
