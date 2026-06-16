package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.ChapterPageRequest;
import com.dayz.sc.course.model.dto.CreateChapterRequest;
import com.dayz.sc.course.model.dto.UpdateChapterRequest;
import com.dayz.sc.course.model.vo.ChapterInteractionVO;
import com.dayz.sc.course.model.vo.ChapterVO;
import com.dayz.sc.course.service.ChapterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST 控制器
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/api/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @PostMapping
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    public ApiResponse<UUID> createChapter(
            @Valid @RequestBody CreateChapterRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID teacherId = JwtPrincipalResolver.requireUserId(jwt);
        UUID chapterId = chapterService.createChapter(request, teacherId);
        return ApiResponse.ok(chapterId);
    }

    @GetMapping
    public ApiResponse<PageResponse<ChapterVO>> listChapters(ChapterPageRequest request) {
        PageResponse<ChapterVO> response = chapterService.listChapters(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<ChapterVO> getChapter(
            @PathVariable UUID id,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        UUID userId = jwt != null ? JwtPrincipalResolver.userId(jwt) : null;
        Integer role = jwt != null ? JwtPrincipalResolver.role(jwt) : null;
        ChapterVO chapter = chapterService.getChapter(id, userId, role);
        return ApiResponse.ok(chapter);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<ChapterVO>> listChaptersByCourse(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        UUID userId = jwt != null ? JwtPrincipalResolver.userId(jwt) : null;
        Integer role = jwt != null ? JwtPrincipalResolver.role(jwt) : null;
        List<ChapterVO> chapters = chapterService.listChaptersByCourse(courseId, userId, role);
        return ApiResponse.ok(chapters);
    }

    @GetMapping("/course/{courseId}/tree")
    public ApiResponse<List<ChapterVO>> getChapterTree(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal @Nullable Jwt jwt) {
        UUID userId = jwt != null ? JwtPrincipalResolver.userId(jwt) : null;
        Integer role = jwt != null ? JwtPrincipalResolver.role(jwt) : null;
        List<ChapterVO> tree = chapterService.getChapterTree(courseId, userId, role);
        return ApiResponse.ok(tree);
    }

    @PutMapping("/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> updateChapter(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChapterRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        chapterService.updateChapter(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> deleteChapter(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        chapterService.deleteChapter(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/view")
    public ApiResponse<ChapterInteractionVO> viewChapter(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        ChapterInteractionVO interaction = chapterService.viewChapter(id, userId, role);
        return ApiResponse.ok(interaction);
    }

    @PostMapping("/{id}/like")
    public ApiResponse<ChapterInteractionVO> likeChapter(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        ChapterInteractionVO interaction = chapterService.likeChapter(id, userId, role);
        return ApiResponse.ok(interaction);
    }

    @DeleteMapping("/{id}/like")
    public ApiResponse<ChapterInteractionVO> unlikeChapter(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        ChapterInteractionVO interaction = chapterService.unlikeChapter(id, userId, role);
        return ApiResponse.ok(interaction);
    }
}
