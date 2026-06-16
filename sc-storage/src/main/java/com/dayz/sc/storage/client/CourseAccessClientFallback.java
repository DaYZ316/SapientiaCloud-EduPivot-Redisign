package com.dayz.sc.storage.client;

import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.storage.model.vo.CourseAccess;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Feign 客户端降级实现
 *
 * @author DaYZ
 * @since 2026-06-12
 */
@Slf4j
@Component
public class CourseAccessClientFallback implements CourseAccessClient {

    @Override
    public ApiResponse<CourseAccess> getAccess(UUID courseId) {
        log.warn("CourseAccessClient fallback: getAccess({}), denying all access", courseId);
        return ApiResponse.ok(new CourseAccess(courseId, false, false, false, false));
    }
}
