package com.dayz.sc.course.service;

import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.course.model.dto.ChapterAttachmentRequest;
import com.dayz.sc.course.model.dto.CreateChapterRequest;
import com.dayz.sc.course.model.dto.UpdateChapterRequest;
import com.dayz.sc.course.model.entity.Chapter;
import com.dayz.sc.course.model.entity.ChapterAttachment;
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

/**
 * ChapterServiceTest 相关定义
 *
 * @author DaYZ
 * @since 2026-06-15
 */
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
                0,
                null
        ), teacherId);

        verify(chapterRepository).save(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue().getContent()).contains("src=\"sc-storage-file:" + fileId + "\"");
        assertThat(chapterCaptor.getValue().getContent()).doesNotContain("X-Amz-Signature");
    }

    @Test
    void createChapter_shouldPersistValidatedAttachmentMetadata() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(courseFile(fileId, courseId)));

        chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                null,
                List.of(new ChapterAttachmentRequest(fileId, "Slides")),
                0,
                null
        ), teacherId);

        verify(chapterRepository).save(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue().getAttachments())
                .containsExactly(new ChapterAttachment(fileId, "Slides", "lesson.pdf", "application/pdf", 2048L, null));
    }

    @Test
    void createChapter_shouldPersistRequestedStatus() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);

        chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                null,
                null,
                0,
                1
        ), teacherId);

        verify(chapterRepository).save(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void updateChapter_shouldKeepAttachmentsWhenRequestOmitsThem() {
        UUID chapterId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Chapter chapter = chapter(chapterId, courseId, teacherId, "<p>draft</p>");
        chapter.setAttachments(List.of(new ChapterAttachment(fileId, "Existing", "existing.pdf", "application/pdf", 1024L, null)));
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));

        chapterService.updateChapter(chapterId, new UpdateChapterRequest(
                "Updated",
                null,
                null,
                null,
                null,
                null,
                null
        ), teacherId, 2);

        verify(chapterRepository).update(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue().getAttachments())
                .containsExactly(new ChapterAttachment(fileId, "Existing", "existing.pdf", "application/pdf", 1024L, null));
    }

    @Test
    void updateChapter_shouldClearAttachmentsWhenRequestSendsEmptyList() {
        UUID chapterId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        Chapter chapter = chapter(chapterId, courseId, teacherId, "<p>draft</p>");
        chapter.setAttachments(List.of(new ChapterAttachment(UUID.randomUUID(), "Existing", "existing.pdf", "application/pdf", 1024L, null)));
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));

        chapterService.updateChapter(chapterId, new UpdateChapterRequest(
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null
        ), teacherId, 2);

        verify(chapterRepository).update(chapterCaptor.capture());
        assertThat(chapterCaptor.getValue().getAttachments()).isEmpty();
    }

    @Test
    void createChapter_shouldRejectAttachmentFromAnotherCourse() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(courseFile(fileId, UUID.randomUUID())));

        assertThatThrownBy(() -> chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                null,
                List.of(new ChapterAttachmentRequest(fileId, "Slides")),
                0,
                null
        ), teacherId)).hasMessage("Invalid chapter attachment");
    }

    @Test
    void createChapter_shouldRejectAttachmentWithInvalidUsage() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(storageFile(fileId, courseId)));

        assertThatThrownBy(() -> chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                null,
                List.of(new ChapterAttachmentRequest(fileId, "Image")),
                0,
                null
        ), teacherId)).hasMessage("Invalid chapter attachment");
    }

    @Test
    void createChapter_shouldRejectPendingAttachment() {
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(chapterRepository.existsByCourseIdAndChapterName(courseId, "Intro")).thenReturn(false);
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(storageFile(
                fileId,
                "lesson.pdf",
                "application/pdf",
                2048L,
                "COURSE_FILE",
                "COURSE_PRIVATE",
                "COURSE",
                courseId,
                "PENDING"
        )));

        assertThatThrownBy(() -> chapterService.createChapter(new CreateChapterRequest(
                courseId,
                "Intro",
                null,
                null,
                null,
                List.of(new ChapterAttachmentRequest(fileId, "Slides")),
                0,
                null
        ), teacherId)).hasMessage("Invalid chapter attachment");
    }

    @Test
    void getChapter_shouldResolveAttachmentUrl() {
        UUID chapterId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Chapter chapter = chapter(chapterId, courseId, teacherId, "<p>hello</p>");
        chapter.setAttachments(List.of(new ChapterAttachment(fileId, "Slides", null, null, null, null)));
        when(chapterRepository.findById(chapterId)).thenReturn(Optional.of(chapter));
        when(storageInternalClient.getFile(fileId)).thenReturn(ApiResponse.ok(courseFile(fileId, courseId)));
        when(storageInternalClient.getUrls(any())).thenReturn(ApiResponse.ok(Map.of(fileId, "https://storage.test/lesson.pdf")));

        ChapterVO result = chapterService.getChapter(chapterId, null);

        assertThat(result.attachments()).hasSize(1);
        assertThat(result.attachments().getFirst().fileId()).isEqualTo(fileId);
        assertThat(result.attachments().getFirst().displayName()).isEqualTo("Slides");
        assertThat(result.attachments().getFirst().fileName()).isEqualTo("lesson.pdf");
        assertThat(result.attachments().getFirst().contentType()).isEqualTo("application/pdf");
        assertThat(result.attachments().getFirst().sizeBytes()).isEqualTo(2048L);
        assertThat(result.attachments().getFirst().url()).isEqualTo("https://storage.test/lesson.pdf");
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
                0,
                null
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
                0,
                null
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
        return storageFile(
                fileId,
                "diagram.png",
                "image/png",
                sizeBytes,
                "FORUM_IMAGE",
                "COURSE_PRIVATE",
                "COURSE",
                courseId,
                "READY"
        );
    }

    private StorageObjectInfo courseFile(UUID fileId, UUID courseId) {
        return storageFile(
                fileId,
                "lesson.pdf",
                "application/pdf",
                2048L,
                "COURSE_FILE",
                "COURSE_PRIVATE",
                "COURSE",
                courseId,
                "READY"
        );
    }

    private StorageObjectInfo storageFile(UUID fileId,
                                          String fileName,
                                          String contentType,
                                          long sizeBytes,
                                          String usage,
                                          String visibility,
                                          String scopeType,
                                          UUID scopeId,
                                          String status) {
        return new StorageObjectInfo(
                fileId,
                fileName,
                contentType,
                sizeBytes,
                usage,
                visibility,
                scopeType,
                scopeId,
                status,
                UUID.randomUUID(),
                Instant.now()
        );
    }
}
