package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CourseAiContextClientFallback implements CourseAiContextClient {

    @Override
    public ApiResponse<@NonNull AiCourseContext> getContext(UUID courseId) {
        return ApiResponse.failOf(ErrorCodes.SERVICE_UNAVAILABLE);
    }
}
