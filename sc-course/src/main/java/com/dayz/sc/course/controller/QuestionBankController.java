package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.*;
import com.dayz.sc.course.model.vo.QuestionBankVO;
import com.dayz.sc.course.model.vo.QuestionVO;
import com.dayz.sc.course.service.QuestionBankService;
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
@RequestMapping("/api/question-banks")
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    // ==================== QuestionBank ====================

    @PostMapping
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<UUID> createQuestionBank(
            @Valid @RequestBody CreateQuestionBankRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID bankId = questionBankService.createQuestionBank(request, userId);
        return ApiResponse.ok(bankId);
    }

    @GetMapping
    public ApiResponse<PageResponse<QuestionBankVO>> listQuestionBanks(QuestionBankPageRequest request) {
        PageResponse<QuestionBankVO> response = questionBankService.listQuestionBanks(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionBankVO> getQuestionBank(@PathVariable UUID id) {
        QuestionBankVO bank = questionBankService.getQuestionBank(id);
        return ApiResponse.ok(bank);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<List<QuestionBankVO>> listQuestionBanksByCourse(@PathVariable UUID courseId) {
        List<QuestionBankVO> banks = questionBankService.listQuestionBanksByCourse(courseId);
        return ApiResponse.ok(banks);
    }

    @PutMapping("/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> updateQuestionBank(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionBankRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.updateQuestionBank(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> deleteQuestionBank(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.deleteQuestionBank(id, userId, role);
        return ApiResponse.ok(null);
    }

    // ==================== Question ====================

    @PostMapping("/questions")
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    public ApiResponse<UUID> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID questionId = questionBankService.createQuestion(request, userId);
        return ApiResponse.ok(questionId);
    }

    @GetMapping("/questions")
    public ApiResponse<PageResponse<QuestionVO>> listQuestions(
            QuestionPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        PageResponse<QuestionVO> response = questionBankService.listQuestions(request, userId, role);
        return ApiResponse.ok(response);
    }

    @GetMapping("/questions/{id}")
    public ApiResponse<QuestionVO> getQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        QuestionVO question = questionBankService.getQuestion(id, userId, role);
        return ApiResponse.ok(question);
    }

    @PutMapping("/questions/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.updateQuestion(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/questions/{id}")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> deleteQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.deleteQuestion(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PutMapping("/questions/{id}/publish")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> publishQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.publishQuestion(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PostMapping("/questions/{id}/view")
    @RateLimited(maxRequests = 10, windowSeconds = 60)
    public ApiResponse<Void> viewQuestion(@PathVariable UUID id) {
        questionBankService.viewQuestion(id);
        return ApiResponse.ok(null);
    }
}
