package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.CreateChapterRequest;
import com.dayz.sc.course.model.dto.ChapterPageRequest;
import com.dayz.sc.course.model.dto.UpdateChapterRequest;
import com.dayz.sc.course.model.vo.ChapterVO;
import com.dayz.sc.course.service.ChapterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
    public ApiResponse<ChapterVO> getChapter(@PathVariable UUID id) {
        ChapterVO chapter = chapterService.getChapter(id);
        return ApiResponse.ok(chapter);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<ChapterVO>> listChaptersByCourse(@PathVariable UUID courseId) {
        List<ChapterVO> chapters = chapterService.listChaptersByCourse(courseId);
        return ApiResponse.ok(chapters);
    }

    @GetMapping("/course/{courseId}/tree")
    public ApiResponse<List<ChapterVO>> getChapterTree(@PathVariable UUID courseId) {
        List<ChapterVO> tree = chapterService.getChapterTree(courseId);
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
}
