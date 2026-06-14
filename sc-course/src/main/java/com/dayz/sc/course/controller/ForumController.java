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

    @GetMapping("/course/{courseId}/comments")
    public ApiResponse<PageResponse<ForumPostVO>> listCourseComments(
            @PathVariable UUID courseId,
            @RequestParam(required = false) Long page,
            @RequestParam(required = false) Long size,
            @AuthenticationPrincipal Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        PageResponse<ForumPostVO> response = forumService.listCourseComments(courseId, page, size);
        return ApiResponse.ok(response);
    }

    @PostMapping("/course/{courseId}/comments")
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    public ApiResponse<UUID> createCourseComment(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateCourseCommentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        UUID commentId = forumService.createCourseComment(courseId, request, userId, role);
        return ApiResponse.ok(commentId);
    }

    @PutMapping("/posts/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
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
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> deletePost(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.deletePost(id, userId, role);
        return ApiResponse.ok(null);
    }

    @GetMapping("/comments/{postId}/replies/tree")
    public ApiResponse<List<ForumReplyVO>> getCommentReplyTree(
            @PathVariable UUID postId,
            @AuthenticationPrincipal Jwt jwt) {
        JwtPrincipalResolver.requireUserId(jwt);
        List<ForumReplyVO> tree = forumService.getCourseCommentReplyTree(postId);
        return ApiResponse.ok(tree);
    }

    @PostMapping("/comments/{postId}/replies")
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    public ApiResponse<UUID> createCourseCommentReply(
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
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> updateReply(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateForumReplyRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.updateReply(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/replies/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> deleteReply(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        forumService.deleteReply(id, userId, role);
        return ApiResponse.ok(null);
    }
}
