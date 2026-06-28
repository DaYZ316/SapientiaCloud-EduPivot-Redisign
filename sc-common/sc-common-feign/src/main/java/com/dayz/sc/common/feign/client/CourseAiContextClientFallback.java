package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.AgentSearchResult;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.feign.dto.ClassSessionAiAccess;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * CourseAiContextClientFallback.
 *
 * @author DaYZ
 */
@Component
public class CourseAiContextClientFallback implements CourseAiContextClient {

    @Override
    public ApiResponse<@NonNull AiCourseContext> getContext(UUID courseId) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<@NonNull List<AgentSearchResult>> search(
            String keyword, UUID courseId, Integer limit, String userId, String userRole) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<@NonNull List<AgentSearchResult>> resources(
            String keyword,
            UUID courseId,
            String courseTitle,
            List<String> types,
            Integer limit,
            String userId,
            String userRole) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<@NonNull List<AgentSearchItem>> courses(
            String scope, Integer limit, String userId, String userRole) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<@NonNull List<@NonNull UUID>> visibleCourseIds(String userId, String userRole) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<@NonNull List<AgentSearchItem>> chapters(
            UUID courseId, String courseTitle, Integer limit, String userId, String userRole) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<@NonNull ClassSessionAiAccess> classSessionAccess(
            UUID classSessionId, String userId, String userRole) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }
}
