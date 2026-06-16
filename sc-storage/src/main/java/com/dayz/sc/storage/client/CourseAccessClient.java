package com.dayz.sc.storage.client;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.storage.model.vo.CourseAccess;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign 客户端接口
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@FeignClient(name = "sc-course", path = "/internal/courses", fallback = CourseAccessClientFallback.class)
public interface CourseAccessClient {
    @GetMapping("/{courseId}/access")
    ApiResponse<CourseAccess> getAccess(@PathVariable("courseId") UUID courseId);
}
