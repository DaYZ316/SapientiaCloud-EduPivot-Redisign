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
import com.dayz.sc.course.model.dto.ChapterAttachmentRequest;
import com.dayz.sc.course.model.dto.ChapterPageRequest;
import com.dayz.sc.course.model.dto.CreateChapterRequest;
import com.dayz.sc.course.model.dto.UpdateChapterRequest;
import com.dayz.sc.course.model.entity.Chapter;
import com.dayz.sc.course.model.entity.ChapterAttachment;
import com.dayz.sc.course.model.enums.ChapterStatus;
import com.dayz.sc.course.model.enums.EnrollmentStatus;
import com.dayz.sc.course.model.vo.ChapterAttachmentVO;
import com.dayz.sc.course.model.vo.ChapterInteractionVO;
import com.dayz.sc.course.model.vo.ChapterVO;
import com.dayz.sc.course.repository.ChapterLikeRepository;
import com.dayz.sc.course.repository.ChapterRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 业务服务
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterService {

    private static final Duration VIEW_DEDUPLICATE_TTL = Duration.ofMinutes(30);
    private static final String VIEW_KEY_PREFIX = "course:chapter:view:";
    private static final String STORAGE_FILE_SRC_PREFIX = "sc-storage-file:";
    private static final String FORUM_IMAGE_USAGE = "FORUM_IMAGE";
    private static final String COURSE_SCOPE_TYPE = "COURSE";
    private static final String COURSE_FILE_USAGE = "COURSE_FILE";
    private static final String COURSE_PUBLIC_FILE_USAGE = "COURSE_PUBLIC_FILE";
    private static final String COURSE_PRIVATE_FILE_USAGE = "COURSE_PRIVATE_FILE";
    private static final String READY_STATUS = "READY";
    private static final long CHAPTER_IMAGE_MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORAGE_FILE_SRC_PATTERN = Pattern.compile("src\\s*=\\s*([\"'])sc-storage-file:([0-9a-fA-F-]{36})\\1");
    private static final Pattern SRC_ATTR_PATTERN = Pattern.compile("src\\s*=\\s*([\"'])([^\"']*)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORAGE_FILE_ID_PATTERN = Pattern.compile("data-storage-file-id\\s*=\\s*([\"'])([0-9a-fA-F-]{36})\\1");
    private final ChapterRepository chapterRepository;
    private final ChapterLikeRepository chapterLikeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseTeacherRepository courseTeacherRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final StorageInternalClient storageInternalClient;

    @Transactional(rollbackFor = Exception.class)
    public UUID createChapter(CreateChapterRequest request, UUID teacherId) {
        if (chapterRepository.existsByCourseIdAndChapterName(request.courseId(), request.chapterName())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "章节名称已存在");
        }
        validateChapterImages(request.courseId(), request.content());
        List<ChapterAttachment> attachments = normalizeChapterAttachments(request.courseId(), request.attachments());
        int status = request.status() == null ? ChapterStatus.DRAFT.getCode() : ChapterStatus.fromCode(request.status()).getCode();

        Chapter chapter = new Chapter();
        chapter.setCourseId(request.courseId());
        chapter.setTeacherId(teacherId);
        chapter.setChapterName(request.chapterName());
        chapter.setParentChapterId(request.parentChapterId());
        chapter.setDescription(request.description());
        chapter.setContent(normalizeChapterImageSources(request.content()));
        chapter.setAttachments(attachments);
        chapter.setSortOrder(request.sortOrder());
        chapter.setStatus(status);
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
            validateChapterImages(chapter.getCourseId(), request.content());
            chapter.setContent(normalizeChapterImageSources(request.content()));
        }
        if (request.attachments() != null) {
            chapter.setAttachments(normalizeChapterAttachments(chapter.getCourseId(), request.attachments()));
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

    public ChapterVO getChapter(UUID chapterId, @Nullable UUID userId, @Nullable Integer role) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND));
        if (!canViewChapter(chapter, userId, role)) {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }
        boolean likedByMe = userId != null && chapterLikeRepository.existsByChapterIdAndUserId(chapterId, userId);
        Map<UUID, String> imageUrlMap = loadImageUrlMap(extractChapterImageIds(chapter.getContent()));
        List<UUID> attachmentIds = extractChapterAttachmentIds(chapter.getAttachments());
        Map<UUID, String> attachmentUrlMap = loadStorageUrlMap(attachmentIds);
        Map<UUID, StorageObjectInfo> attachmentInfoMap = loadStorageInfoMap(attachmentIds);
        return toChapterVO(chapter, null, likedByMe, imageUrlMap, attachmentUrlMap, attachmentInfoMap);
    }

    public ChapterVO getChapter(UUID chapterId, @Nullable UUID userId) {
        return getChapter(chapterId, userId, null);
    }

    public PageResponse<@NonNull ChapterVO> listChapters(ChapterPageRequest request) {
        int page = PageUtils.normalizePage(request.page());
        int size = PageUtils.normalizeSize(request.size());

        Page<Chapter> result = chapterRepository.findAll(page, size,
                request.courseId(), request.status(), request.keyword());

        List<Chapter> chapters = result.getRecords();
        Map<UUID, String> imageUrlMap = loadImageUrlMap(chapters.stream()
                .flatMap(chapter -> extractChapterImageIds(chapter.getContent()).stream())
                .distinct()
                .toList());
        List<UUID> attachmentIds = chapters.stream()
                .flatMap(chapter -> extractChapterAttachmentIds(chapter.getAttachments()).stream())
                .distinct()
                .toList();
        Map<UUID, String> attachmentUrlMap = loadStorageUrlMap(attachmentIds);
        Map<UUID, StorageObjectInfo> attachmentInfoMap = loadStorageInfoMap(attachmentIds);
        List<ChapterVO> voList = chapters.stream()
                .map(ch -> toChapterVO(ch, null, false, imageUrlMap, attachmentUrlMap, attachmentInfoMap))
                .toList();

        return new PageResponse<>(voList, result.getTotal(), page, size);
    }

    public List<ChapterVO> listChaptersByCourse(UUID courseId, @Nullable UUID userId, @Nullable Integer role) {
        List<Chapter> chapters = chapterRepository.findByCourseId(courseId);
        return buildChapterTree(filterVisibleChapters(chapters, userId, role), userId);
    }

    public List<ChapterVO> listChaptersByCourse(UUID courseId, @Nullable UUID userId) {
        return listChaptersByCourse(courseId, userId, null);
    }

    public List<ChapterVO> getChapterTree(UUID courseId, @Nullable UUID userId, @Nullable Integer role) {
        List<Chapter> allChapters = chapterRepository.findByCourseId(courseId);
        return buildChapterTree(filterVisibleChapters(allChapters, userId, role), userId);
    }

    public List<ChapterVO> getChapterTree(UUID courseId, @Nullable UUID userId) {
        return getChapterTree(courseId, userId, null);
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

    private List<Chapter> filterVisibleChapters(List<Chapter> chapters, @Nullable UUID userId, @Nullable Integer role) {
        return chapters.stream()
                .filter(chapter -> canViewChapter(chapter, userId, role))
                .toList();
    }

    private boolean canViewChapter(Chapter chapter, @Nullable UUID userId, @Nullable Integer role) {
        if (Objects.equals(chapter.getStatus(), ChapterStatus.PUBLISHED.getCode())) {
            return true;
        }
        return userId != null && userId.equals(chapter.getTeacherId());
    }

    private void validateChapterImages(UUID courseId, String content) {
        validateImageTags(content);
        for (UUID fileId : extractChapterImageIds(content)) {
            StorageObjectInfo file = requireStorageFile(fileId, "Invalid chapter image");
            if (!READY_STATUS.equals(file.status())
                    || !FORUM_IMAGE_USAGE.equals(file.usage())
                    || !COURSE_SCOPE_TYPE.equals(file.scopeType())
                    || !courseId.equals(file.scopeId())) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid chapter image");
            }
            if (file.sizeBytes() > CHAPTER_IMAGE_MAX_SIZE_BYTES) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Chapter image is too large");
            }
        }
    }

    private void validateImageTags(String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        Matcher matcher = IMG_TAG_PATTERN.matcher(content);
        while (matcher.find()) {
            if (firstImageId(matcher.group()) == null) {
                throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid chapter image");
            }
        }
    }

    private String normalizeChapterImageSources(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        Matcher matcher = IMG_TAG_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(normalizeImageTagSource(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalizeImageTagSource(String imageTag) {
        UUID fileId = firstImageId(imageTag);
        if (fileId == null) {
            return imageTag;
        }

        String normalizedSrc = STORAGE_FILE_SRC_PREFIX + fileId;
        Matcher srcMatcher = SRC_ATTR_PATTERN.matcher(imageTag);
        if (srcMatcher.find()) {
            return srcMatcher.replaceFirst(Matcher.quoteReplacement("src=" + srcMatcher.group(1) + normalizedSrc + srcMatcher.group(1)));
        }
        return imageTag.replaceFirst("(?i)<img\\b", "<img src=\"" + normalizedSrc + "\"");
    }

    private UUID firstImageId(String content) {
        Matcher idMatcher = STORAGE_FILE_ID_PATTERN.matcher(content);
        while (idMatcher.find()) {
            UUID fileId = parseUuidOrNull(idMatcher.group(2));
            if (fileId != null) {
                return fileId;
            }
        }

        Matcher srcMatcher = STORAGE_FILE_SRC_PATTERN.matcher(content);
        while (srcMatcher.find()) {
            UUID fileId = parseUuidOrNull(srcMatcher.group(2));
            if (fileId != null) {
                return fileId;
            }
        }
        return null;
    }

    private StorageObjectInfo requireStorageFile(UUID fileId, String invalidMessage) {
        ApiResponse<@NonNull StorageObjectInfo> response = storageInternalClient.getFile(fileId);
        if (response == null || response.code() != ErrorCodes.SUCCESS.code() || response.data() == null) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, invalidMessage);
        }
        return response.data();
    }

    private List<ChapterAttachment> normalizeChapterAttachments(UUID courseId, @Nullable List<ChapterAttachmentRequest> requests) {
        if (requests == null) {
            return null;
        }
        if (requests.isEmpty()) {
            return List.of();
        }

        List<ChapterAttachment> attachments = new ArrayList<>(requests.size());
        for (ChapterAttachmentRequest request : requests) {
            StorageObjectInfo file = requireStorageFile(request.fileId(), "Invalid chapter attachment");
            validateChapterAttachment(file, courseId);
            String displayName = StringUtils.hasText(request.displayName())
                    ? request.displayName().trim()
                    : file.fileName();
            attachments.add(new ChapterAttachment(
                    file.id(),
                    displayName,
                    file.fileName(),
                    file.contentType(),
                    file.sizeBytes(),
                    null
            ));
        }
        return attachments;
    }

    private void validateChapterAttachment(StorageObjectInfo file, UUID courseId) {
        if (!READY_STATUS.equals(file.status())
                || !isAllowedCourseFileUsage(file.usage())
                || !COURSE_SCOPE_TYPE.equals(file.scopeType())
                || !courseId.equals(file.scopeId())) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "Invalid chapter attachment");
        }
    }

    private boolean isAllowedCourseFileUsage(String usage) {
        return COURSE_FILE_USAGE.equals(usage)
                || COURSE_PUBLIC_FILE_USAGE.equals(usage)
                || COURSE_PRIVATE_FILE_USAGE.equals(usage);
    }

    private List<UUID> extractChapterImageIds(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        addStorageIds(content, STORAGE_FILE_SRC_PATTERN, ids);
        addStorageIds(content, STORAGE_FILE_ID_PATTERN, ids);
        return List.copyOf(ids);
    }

    private void addStorageIds(String content, Pattern pattern, Set<UUID> ids) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            UUID fileId = parseUuidOrNull(matcher.group(2));
            if (fileId != null) {
                ids.add(fileId);
            }
        }
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Map<UUID, String> loadImageUrlMap(List<UUID> imageIds) {
        return loadStorageUrlMap(imageIds);
    }

    private Map<UUID, String> loadStorageUrlMap(List<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        ApiResponse<@NonNull Map<@NonNull UUID, @NonNull String>> response = storageInternalClient.getUrls(fileIds);
        if (response == null || response.code() != ErrorCodes.SUCCESS.code() || response.data() == null) {
            return Map.of();
        }
        return response.data();
    }

    private Map<UUID, StorageObjectInfo> loadStorageInfoMap(List<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, StorageObjectInfo> result = new HashMap<>(fileIds.size());
        for (UUID fileId : fileIds) {
            ApiResponse<@NonNull StorageObjectInfo> response = storageInternalClient.getFile(fileId);
            if (response != null && response.code() == ErrorCodes.SUCCESS.code() && response.data() != null) {
                result.put(fileId, response.data());
            }
        }
        return result;
    }

    private String resolveChapterImageUrls(String content, Map<UUID, String> imageUrlMap) {
        if (content == null || content.isBlank() || imageUrlMap.isEmpty()) {
            return content;
        }

        Matcher matcher = STORAGE_FILE_SRC_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            UUID fileId = parseUuidOrNull(matcher.group(2));
            String resolvedUrl = fileId == null ? null : imageUrlMap.get(fileId);
            if (resolvedUrl == null || resolvedUrl.isBlank()) {
                continue;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement("src=" + matcher.group(1) + resolvedUrl + matcher.group(1)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private List<UUID> extractChapterAttachmentIds(@Nullable List<ChapterAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(ChapterAttachment::fileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private List<ChapterAttachmentVO> resolveChapterAttachments(@Nullable List<ChapterAttachment> attachments,
                                                                Map<UUID, String> attachmentUrlMap,
                                                                Map<UUID, StorageObjectInfo> attachmentInfoMap) {
        if (attachments == null) {
            return null;
        }
        List<ChapterAttachmentVO> result = new ArrayList<>(attachments.size());
        for (int i = 0; i < attachments.size(); i++) {
            ChapterAttachment attachment = attachments.get(i);
            StorageObjectInfo fileInfo = attachment.fileId() == null ? null : attachmentInfoMap.get(attachment.fileId());
            String fileName = firstText(attachment.fileName(), fileInfo == null ? null : fileInfo.fileName(), legacyName(attachment.legacyUrl(), i + 1));
            String displayName = firstText(attachment.displayName(), fileName);
            String url = attachment.fileId() == null ? attachment.legacyUrl() : attachmentUrlMap.get(attachment.fileId());
            result.add(new ChapterAttachmentVO(
                    attachment.fileId(),
                    displayName,
                    fileName,
                    firstTextOrNull(attachment.contentType(), fileInfo == null ? null : fileInfo.contentType()),
                    attachment.sizeBytes() == null && fileInfo != null ? fileInfo.sizeBytes() : attachment.sizeBytes(),
                    url
            ));
        }
        return result;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "Attachment";
    }

    private String firstTextOrNull(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String legacyName(@Nullable String url, int index) {
        if (!StringUtils.hasText(url)) {
            return "Attachment " + index;
        }
        String normalized = url;
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        int slashIndex = normalized.lastIndexOf('/');
        String name = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        return StringUtils.hasText(name) ? name : "Attachment " + index;
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
        Map<UUID, String> imageUrlMap = loadImageUrlMap(allChapters.stream()
                .flatMap(chapter -> extractChapterImageIds(chapter.getContent()).stream())
                .distinct()
                .toList());
        List<UUID> attachmentIds = allChapters.stream()
                .flatMap(chapter -> extractChapterAttachmentIds(chapter.getAttachments()).stream())
                .distinct()
                .toList();
        Map<UUID, String> attachmentUrlMap = loadStorageUrlMap(attachmentIds);
        Map<UUID, StorageObjectInfo> attachmentInfoMap = loadStorageInfoMap(attachmentIds);
        return allChapters.stream()
                .filter(ch -> ch.getParentChapterId() == null)
                .map(ch -> toChapterVO(ch, childrenMap, finalLikedChapterIds.contains(ch.getId()), finalLikedChapterIds, imageUrlMap, attachmentUrlMap, attachmentInfoMap))
                .toList();
    }

    private ChapterVO toChapterVO(Chapter chapter, Map<UUID, List<Chapter>> childrenMap, Set<UUID> likedChapterIds) {
        return toChapterVO(chapter, childrenMap, likedChapterIds.contains(chapter.getId()), likedChapterIds, Map.of(), Map.of(), Map.of());
    }

    private ChapterVO toChapterVO(Chapter chapter, @Nullable Map<UUID, List<Chapter>> childrenMap, boolean likedByMe) {
        return toChapterVO(chapter, childrenMap, likedByMe, Set.of(), Map.of(), Map.of(), Map.of());
    }

    private ChapterVO toChapterVO(Chapter chapter,
                                  @Nullable Map<UUID, List<Chapter>> childrenMap,
                                  boolean likedByMe,
                                  Map<UUID, String> imageUrlMap) {
        return toChapterVO(chapter, childrenMap, likedByMe, Set.of(), imageUrlMap, Map.of(), Map.of());
    }

    private ChapterVO toChapterVO(Chapter chapter,
                                  @Nullable Map<UUID, List<Chapter>> childrenMap,
                                  boolean likedByMe,
                                  Map<UUID, String> imageUrlMap,
                                  Map<UUID, String> attachmentUrlMap) {
        return toChapterVO(chapter, childrenMap, likedByMe, Set.of(), imageUrlMap, attachmentUrlMap, Map.of());
    }

    private ChapterVO toChapterVO(Chapter chapter,
                                  @Nullable Map<UUID, List<Chapter>> childrenMap,
                                  boolean likedByMe,
                                  Map<UUID, String> imageUrlMap,
                                  Map<UUID, String> attachmentUrlMap,
                                  Map<UUID, StorageObjectInfo> attachmentInfoMap) {
        return toChapterVO(chapter, childrenMap, likedByMe, Set.of(), imageUrlMap, attachmentUrlMap, attachmentInfoMap);
    }

    private ChapterVO toChapterVO(Chapter chapter,
                                  @Nullable Map<UUID, List<Chapter>> childrenMap,
                                  boolean likedByMe,
                                  Set<UUID> likedChapterIds,
                                  Map<UUID, String> imageUrlMap,
                                  Map<UUID, String> attachmentUrlMap,
                                  Map<UUID, StorageObjectInfo> attachmentInfoMap) {
        List<ChapterVO> children = null;
        if (childrenMap != null) {
            List<Chapter> childChapters = childrenMap.getOrDefault(chapter.getId(), List.of());
            children = childChapters.stream()
                    .map(ch -> toChapterVO(ch, childrenMap, likedChapterIds.contains(ch.getId()), likedChapterIds, imageUrlMap, attachmentUrlMap, attachmentInfoMap))
                    .toList();
        }

        return new ChapterVO(
                chapter.getId(),
                chapter.getCourseId(),
                chapter.getTeacherId(),
                chapter.getChapterName(),
                chapter.getParentChapterId(),
                chapter.getDescription(),
                resolveChapterImageUrls(chapter.getContent(), imageUrlMap),
                resolveChapterAttachments(chapter.getAttachments(), attachmentUrlMap, attachmentInfoMap),
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
