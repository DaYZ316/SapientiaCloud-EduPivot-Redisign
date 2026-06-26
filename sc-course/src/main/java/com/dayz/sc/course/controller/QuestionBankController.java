package com.dayz.sc.course.controller;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.response.PageResponse;
import com.dayz.sc.common.question.CreateQuestionRequest;
import com.dayz.sc.common.security.ratelimit.RateLimited;
import com.dayz.sc.common.security.support.JwtPrincipalResolver;
import com.dayz.sc.course.model.dto.*;
import com.dayz.sc.course.model.vo.BatchCreateQuestionsResponse;
import com.dayz.sc.course.model.vo.QuestionBankVO;
import com.dayz.sc.course.model.vo.QuestionVO;
import com.dayz.sc.course.service.QuestionBankService;
import jakarta.validation.Valid;
import org.jspecify.annotations.NonNull;
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
    @RateLimited
    public ApiResponse<@NonNull UUID> createQuestionBank(
            @Valid @RequestBody CreateQuestionBankRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID bankId = questionBankService.createQuestionBank(request, userId);
        return ApiResponse.ok(bankId);
    }

    @GetMapping
    public ApiResponse<@NonNull PageResponse<@NonNull QuestionBankVO>> listQuestionBanks(
            QuestionBankPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        PageResponse<@NonNull QuestionBankVO> response = questionBankService.listQuestionBanks(request, userId, role);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<@NonNull QuestionBankVO> getQuestionBank(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        QuestionBankVO bank = questionBankService.getQuestionBank(id, userId, role);
        return ApiResponse.ok(bank);
    }

    @GetMapping("/course/{courseId}")
    public ApiResponse<@NonNull List<@NonNull QuestionBankVO>> listQuestionBanksByCourse(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        List<QuestionBankVO> banks = questionBankService.listQuestionBanksByCourse(courseId, userId, role);
        return ApiResponse.ok(banks);
    }

    @PutMapping("/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> updateQuestionBank(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionBankRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.updateQuestionBank(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> deleteQuestionBank(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.deleteQuestionBank(id, userId, role);
        return ApiResponse.ok(null);
    }

    // ==================== Question ====================

    @PostMapping("/questions")
    @RateLimited(maxRequests = 30)
    public ApiResponse<@NonNull UUID> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        UUID questionId = questionBankService.createQuestion(request, userId);
        return ApiResponse.ok(questionId);
    }

    @PostMapping("/questions/batch")
    @RateLimited(maxRequests = 10)
    public ApiResponse<@NonNull BatchCreateQuestionsResponse> batchCreateQuestions(
            @Valid @RequestBody BatchCreateQuestionsRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        BatchCreateQuestionsResponse response = questionBankService.batchCreateQuestions(request, userId, role);
        return ApiResponse.ok(response);
    }

    @GetMapping("/questions")
    public ApiResponse<@NonNull PageResponse<@NonNull QuestionVO>> listQuestions(
            QuestionPageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        PageResponse<@NonNull QuestionVO> response = questionBankService.listQuestions(request, userId, role);
        return ApiResponse.ok(response);
    }

    @GetMapping("/questions/{id}")
    public ApiResponse<@NonNull QuestionVO> getQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        QuestionVO question = questionBankService.getQuestion(id, userId, role);
        return ApiResponse.ok(question);
    }

    @PutMapping("/questions/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.updateQuestion(id, request, userId, role);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/questions/{id}")
    @RateLimited
    public ApiResponse<@NonNull Void> deleteQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.deleteQuestion(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PutMapping("/questions/{id}/publish")
    @RateLimited
    public ApiResponse<@NonNull Void> publishQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.publishQuestion(id, userId, role);
        return ApiResponse.ok(null);
    }

    @PostMapping("/questions/{id}/view")
    @RateLimited
    public ApiResponse<@NonNull Void> viewQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = JwtPrincipalResolver.requireUserId(jwt);
        Integer role = JwtPrincipalResolver.role(jwt);
        questionBankService.viewQuestion(id, userId, role);
        return ApiResponse.ok(null);
    }
}
