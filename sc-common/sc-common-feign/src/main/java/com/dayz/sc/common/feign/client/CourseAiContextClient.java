package com.dayz.sc.common.feign.client;

import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.AgentSearchResult;
import com.dayz.sc.common.feign.dto.AiCourseContext;
import com.dayz.sc.common.feign.dto.ClassSessionAiAccess;
import com.dayz.sc.common.response.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Course 服务 AI 上下文内部调用客户端
 *
 * @author DaYZ
 * @since 2026-06-27
 */
@FeignClient(
        contextId = "courseAiContextClient",
        name = "sc-course",
        path = "/internal/ai",
        fallback = CourseAiContextClientFallback.class)
public interface CourseAiContextClient {

    String HEADER_USER_ID = "X-User-Id";
    String HEADER_USER_ROLE = "X-User-Role";

    /**
     * 获取课程 AI 上下文
     *
     * @param courseId 课程ID，可选
     * @return 课程上下文信息
     */
    @GetMapping("/context")
    ApiResponse<@NonNull AiCourseContext> getContext(@RequestParam(value = "courseId", required = false) UUID courseId);

    /**
     * 搜索课程内容
     *
     * @param keyword  搜索关键词
     * @param courseId 课程ID，可选
     * @param limit    结果数量限制，可选
     * @param userId   用户ID
     * @param userRole 用户角色
     * @return 搜索结果列表
     */
    @GetMapping("/search")
    ApiResponse<@NonNull List<AgentSearchResult>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "courseId", required = false) UUID courseId,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    /**
     * 搜索课程资源
     *
     * @param keyword     搜索关键词
     * @param courseId    课程ID，可选
     * @param courseTitle 课程标题，可选
     * @param types       资源类型列表，可选
     * @param limit       结果数量限制，可选
     * @param userId      用户ID
     * @param userRole    用户角色
     * @return 搜索结果列表
     */
    @GetMapping("/resources")
    ApiResponse<@NonNull List<AgentSearchResult>> resources(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "courseId", required = false) UUID courseId,
            @RequestParam(value = "courseTitle", required = false) String courseTitle,
            @RequestParam(value = "types", required = false) List<String> types,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    /**
     * 查询用户课程列表
     *
     * @param scope    课程范围，可选
     * @param limit    结果数量限制，可选
     * @param userId   用户ID
     * @param userRole 用户角色
     * @return 课程列表
     */
    @GetMapping("/courses")
    ApiResponse<@NonNull List<AgentSearchItem>> courses(
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    /**
     * Query course IDs visible to the current user for AI summary access filtering.
     *
     * @param userId   user ID
     * @param userRole user role
     * @return visible course ID list
     */
    @GetMapping("/visible-course-ids")
    ApiResponse<@NonNull List<@NonNull UUID>> visibleCourseIds(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    /**
     * 查询课程章节列表
     *
     * @param courseId    课程ID，可选
     * @param courseTitle 课程标题，可选
     * @param limit       结果数量限制，可选
     * @param userId      用户ID
     * @param userRole    用户角色
     * @return 章节列表
     */
    @GetMapping("/chapters")
    ApiResponse<@NonNull List<AgentSearchItem>> chapters(
            @RequestParam(value = "courseId", required = false) UUID courseId,
            @RequestParam(value = "courseTitle", required = false) String courseTitle,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);

    /**
     * 获取课堂会话 AI 访问权限
     *
     * @param classSessionId 课堂会话ID
     * @param userId         用户ID
     * @param userRole       用户角色
     * @return 课堂会话 AI 访问权限信息
     */
    @GetMapping("/class-session-access")
    ApiResponse<@NonNull ClassSessionAiAccess> classSessionAccess(
            @RequestParam("classSessionId") UUID classSessionId,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLE, required = false) String userRole);
}
