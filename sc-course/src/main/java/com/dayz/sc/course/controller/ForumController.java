package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.CreateCourseCommentReplyRequest;
import com.dayz.sc.course.model.dto.CreateCourseCommentRequest;
import com.dayz.sc.course.model.dto.UpdateForumPostRequest;
import com.dayz.sc.course.model.dto.UpdateForumReplyRequest;
import com.dayz.sc.course.model.vo.ForumPostVO;
import com.dayz.sc.course.model.vo.ForumReplyVO;
import com.dayz.sc.course.service.ForumService;
import org.jspecify.annotations.NonNull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/forums")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;

    @GetMapping("/course/{courseId}/comments")
    public ApiResponse<@NonNull PageResponse<@NonNull ForumPostVO>> listCourseComments(
            @PathVariable UUID courseId,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        PageResponse<@NonNull ForumPostVO> response = forumService.listCourseComments(courseId, page, size, userId, role);
        return ApiResponse.ok(response);
    }

    @PostMapping("/course/{courseId}/comments")
    @RateLimited(maxRequests = 20)
    public ApiResponse<@NonNull UUID> createCourseComment(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateCourseCommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        UUID commentId = forumService.createCourseComment(courseId, request, userId, role);
        return ApiResponse.ok(commentId);
    }

    @PutMapping("/posts/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumPostRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.updatePost(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/posts/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> deletePost(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.deletePost(id, userId, role);
        return ApiResponse.ok(null);
    }

    @GetMapping("/comments/{postId}/replies/tree")
    public ApiResponse<@NonNull List<@NonNull ForumReplyVO>> getCommentReplyTree(
            @PathVariable UUID postId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        List<ForumReplyVO> tree = forumService.getCourseCommentReplyTree(postId, userId, role);
        return ApiResponse.ok(tree);
    }

    @PostMapping("/comments/{postId}/replies")
    @RateLimited(maxRequests = 30)
    public ApiResponse<@NonNull UUID> createCourseCommentReply(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCourseCommentReplyRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        UUID replyId = forumService.createCourseCommentReply(postId, request, userId, role, ipAddress, userAgent);
        return ApiResponse.ok(replyId);
    }

    @PutMapping("/replies/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> updateReply(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumReplyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.updateReply(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/replies/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> deleteReply(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.deleteReply(id, userId, role);
        return ApiResponse.ok(null);
    }
}
