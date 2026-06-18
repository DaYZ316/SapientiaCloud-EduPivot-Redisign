package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        contextId = "courseAiContextClient",
        name = "sc-course",
        path = "/internal/ai",
        fallback = CourseAiContextClientFallback.class)
public interface CourseAiContextClient {

    @GetMapping("/context")
    ApiResponse<@NonNull AiCourseContext> getContext(@RequestParam(value = "courseId", required = false) UUID courseId);
}
