package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.*;
import com.dayz.sc.course.model.vo.ForumPostVO;
import com.dayz.sc.course.model.vo.ForumReplyVO;
import com.dayz.sc.course.model.vo.ForumVO;
import com.dayz.sc.course.service.ForumService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/forums")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;

    // ==================== Forum ====================

    @PostMapping
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<UUID> createForum(
            @Valid @RequestBody CreateForumRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID forumId = forumService.createForum(request, userId);
        return ApiResponse.ok(forumId);
    }

    @GetMapping
    public ApiResponse<PageResponse<ForumVO>> listForums(ForumPageRequest request) {
        PageResponse<ForumVO> response = forumService.listForums(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<ForumVO> getForum(@PathVariable UUID id) {
        ForumVO forum = forumService.getForum(id);
        return ApiResponse.ok(forum);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<ForumVO>> listForumsByCourse(@PathVariable UUID courseId) {
        List<ForumVO> forums = forumService.listForumsByCourse(courseId);
        return ApiResponse.ok(forums);
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updateForum(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.updateForum(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteForum(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.deleteForum(id, userId, role);
        return ApiResponse.ok(null);
    }

    // ==================== Post ====================

    @PostMapping("/posts")
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    public ApiResponse<UUID> createPost(
            @Valid @RequestBody CreateForumPostRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID postId = forumService.createPost(request, userId);
        return ApiResponse.ok(postId);
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<ForumPostVO>> listPosts(ForumPostPageRequest request) {
        PageResponse<ForumPostVO> response = forumService.listPosts(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<ForumPostVO> getPost(@PathVariable UUID id) {
        ForumPostVO post = forumService.getPost(id);
        return ApiResponse.ok(post);
    }

    @GetMapping("/posts/hot")
    public ApiResponse<List<ForumPostVO>> listHotPosts(
            @RequestParam UUID courseId,
            @RequestParam(defaultValue = "10") int limit) {
        List<ForumPostVO> posts = forumService.listHotPosts(courseId, limit);
        return ApiResponse.ok(posts);
    }

    @GetMapping("/posts/latest")
    public ApiResponse<List<ForumPostVO>> listLatestPosts(
            @RequestParam UUID courseId,
            @RequestParam(defaultValue = "10") int limit) {
        List<ForumPostVO> posts = forumService.listLatestPosts(courseId, limit);
        return ApiResponse.ok(posts);
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<Void> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumPostRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.updatePost(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.deletePost(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PutMapping("/posts/{id}/top")
    public ApiResponse<Void> toggleTop(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.toggleTop(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PutMapping("/posts/{id}/essence")
    public ApiResponse<Void> toggleEssence(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.toggleEssence(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PutMapping("/posts/{id}/lock")
    public ApiResponse<Void> toggleLock(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.toggleLock(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<Void> likePost(@PathVariable UUID id) {
        forumService.likePost(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<Void> unlikePost(@PathVariable UUID id) {
        forumService.unlikePost(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/posts/{id}/view")
    public ApiResponse<Void> viewPost(@PathVariable UUID id) {
        forumService.viewPost(id);
        return ApiResponse.ok(null);
    }

    // ==================== Reply ====================

    @PostMapping("/replies")
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    public ApiResponse<UUID> createReply(
            @Valid @RequestBody CreateForumReplyRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        UUID replyId = forumService.createReply(request, userId, ipAddress, userAgent);
        return ApiResponse.ok(replyId);
    }

    @GetMapping("/replies")
    public ApiResponse<PageResponse<ForumReplyVO>> listReplies(ForumReplyPageRequest request) {
        PageResponse<ForumReplyVO> response = forumService.listReplies(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/replies/post/{postId}/tree")
    public ApiResponse<List<ForumReplyVO>> getReplyTree(@PathVariable UUID postId) {
        List<ForumReplyVO> tree = forumService.getReplyTree(postId);
        return ApiResponse.ok(tree);
    }

    @PutMapping("/replies/{id}/accept")
    public ApiResponse<Void> acceptReply(@PathVariable UUID id) {
        forumService.acceptReply(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/replies/{id}/unaccept")
    public ApiResponse<Void> unacceptReply(@PathVariable UUID id) {
        forumService.unacceptReply(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/replies/{id}/like")
    public ApiResponse<Void> likeReply(@PathVariable UUID id) {
        forumService.likeReply(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/replies/{id}/like")
    public ApiResponse<Void> unlikeReply(@PathVariable UUID id) {
        forumService.unlikeReply(id);
        return ApiResponse.ok(null);
    }
}
