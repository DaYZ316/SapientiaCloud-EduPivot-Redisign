package com.dayz.sc.course.service;

import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.course.model.dto.CreateChapterRequest;
import com.dayz.sc.course.model.entity.Chapter;
import com.dayz.sc.course.model.vo.ChapterVO;
import com.dayz.sc.course.repository.ChapterLikeRepository;
import com.dayz.sc.course.repository.ChapterRepository;
import com.dayz.sc.course.repository.CourseTeacherRepository;
import com.dayz.sc.course.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChapterServiceTest {

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private ChapterLikeRepository chapterLikeRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseTeacherRepository courseTeacherRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private StorageInternalClient storageInternalClient;

    @Captor
    private ArgumentCaptor<Chapter> chapterCaptor;

    private ChapterService chapterService;

    @BeforeEach
    void setUp() {
        chapterService = new ChapterService(
                chapterRepository,
                chapterLikeRepository,
                enrollmentRepository,
                courseTeacherRepository,
                stringRedisTemplate,
                storageInternalClient
        );
    }

    @Test
    void createChapter_shouldPersistStableStorageMarker_whenContentContainsResolvedImageUrl() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(storageFile(fileId, courseId)));

        chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                "<p>hello</p><figure class=\"chapter-image\"><img src=\"https://storage.test/tmp?X-Amz-Signature=abc\" data-storage-file-id=\"" + fileId + "\" alt=\"diagram\" loading=\"lazy\"></figure>",
                null,
                0
        ), teacherId);

        verify(chapterRepository).save(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue().getContent()).contains("src=\"sc-storage-file:" + fileId + "\"");
        assertThat(chapterCaptor.getValue().getContent()).doesNotContain("X-Amz-Signature");
    }

    @Test
    void getChapter_shouldResolveStableStorageMarkerToReadableUrl() {
        UUID chapterId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Chapter chapter = chapter(chapterId, courseId, teacherId,
                "<p>hello</p><figure class=\"chapter-image\"><img src=\"sc-storage-file:" + fileId + "\" data-storage-file-id=\"" + fileId + "\" alt=\"diagram\" loading=\"lazy\"></figure>");
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(storageInternalClient.getUrls(any())).thenReturn(ApiResponse.ok(Map.of(fileId, "https://storage.test/current")));

        ChapterVO result = chapterService.getChapter(chapterId, null);

        assertThat(result.content()).contains("src=\"https://storage.test/current\"");
        assertThat(result.content()).contains("data-storage-file-id=\"" + fileId + "\"");
    }

    @Test
    void getChapterTree_shouldHideDraftsFromNonOwners() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Chapter published = chapter(UUID.randomUUID(), courseId, teacherId, "<p>published</p>");
        Chapter draft = chapter(UUID.randomUUID(), courseId, teacherId, "<p>draft</p>");
        draft.setStatus(0);
        when(chapterRepository.findByCourseId(courseId)).thenReturn(List.of(published, draft));

        List<ChapterVO> result = chapterService.getChapterTree(courseId, UUID.randomUUID(), 1);

        assertThat(result).extracting(ChapterVO::id).containsExactly(published.getId());
    }

    @Test
    void getChapterTree_shouldShowDraftsOnlyToOwner() {
        UUID courseId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        Chapter draft = chapter(UUID.randomUUID(), courseId, creatorId, "<p>draft</p>");
        draft.setStatus(0);
        when(chapterRepository.findByCourseId(courseId)).thenReturn(List.of(draft));

        List<ChapterVO> result = chapterService.getChapterTree(courseId, creatorId, 2);

        assertThat(result).extracting(ChapterVO::id).containsExactly(draft.getId());
    }

    @Test
    void getChapterTree_shouldHideDraftsFromAdminWhenNotOwner() {
        UUID courseId = UUID.randomUUID();
        Chapter draft = chapter(UUID.randomUUID(), courseId, UUID.randomUUID(), "<p>draft</p>");
        draft.setStatus(0);
        when(chapterRepository.findByCourseId(courseId)).thenReturn(List.of(draft));

        List<ChapterVO> result = chapterService.getChapterTree(courseId, UUID.randomUUID(), 0);

        assertThat(result).isEmpty();
    }

    @Test
    void createChapter_shouldRejectImageWithoutStorageFileId() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);

        assertThatThrownBy(() -> chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                "<p>hello</p><img src=\"https://example.test/image.png\" alt=\"diagram\">",
                null,
                0
        ), teacherId)).hasMessage("Invalid chapter image");
    }

    @Test
    void createChapter_shouldRejectOversizedImage() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(storageFile(fileId, courseId, 5L * 1024 * 1024 + 1)));

        assertThatThrownBy(() -> chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                "<p>hello</p><figure class=\"chapter-image\"><img src=\"sc-storage-file:" + fileId + "\" data-storage-file-id=\"" + fileId + "\" alt=\"diagram\"></figure>",
                null,
                0
        ), teacherId)).hasMessage("Chapter image is too large");
    }

    private Chapter chapter(UUID chapterId, UUID courseId, UUID teacherId, String content) {
        Chapter chapter = new Chapter();
        chapter.setId(chapterId);
        chapter.setCourseId(courseId);
        chapter.setTeacherId(teacherId);
        chapter.setChapterName("Intro");
        chapter.setContent(content);
        chapter.setSortOrder(0);
        chapter.setStatus(1);
        chapter.setViewCount(0L);
        chapter.setLikeCount(0L);
        chapter.setCreatedAt(Instant.now());
        chapter.setUpdatedAt(Instant.now());
        return chapter;
    }

    private StorageObjectInfo storageFile(UUID fileId, UUID courseId) {
        return storageFile(fileId, courseId, 1024L);
    }

    private StorageObjectInfo storageFile(UUID fileId, UUID courseId, long sizeBytes) {
        return new StorageObjectInfo(
                fileId,
                "diagram.png",
                "image/png",
                sizeBytes,
                "FORUM_IMAGE",
                "COURSE_PRIVATE",
                "COURSE",
                courseId,
                "READY",
                UUID.randomUUID(),
                Instant.now()
        );
    }
}
