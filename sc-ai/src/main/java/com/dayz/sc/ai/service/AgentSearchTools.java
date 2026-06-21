package com.dayz.sc.ai.service;

import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.CurrentUserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AgentSearchTools {

    public static final String CONTEXT_USER_ID = "userId";
    public static final String CONTEXT_USER_ROLE = "userRole";
    public static final String CONTEXT_COURSE_ID = "courseId";
    public static final String CONTEXT_AUTHORIZATION = "authorization";
    public static final String CONTEXT_EVENT_EMITTER = "agentSearchEventEmitter";

    private final AgentSearchService agentSearchService;

    @Tool(name = "getCurrentUserProfile", description = "Get the current logged-in user's display name, account name, avatar, and readable role.")
    public CurrentUserProfile getCurrentUserProfile(ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        Integer role = intFromContext(toolContext, CONTEXT_USER_ROLE);
        String authorization = stringFromContext(toolContext, CONTEXT_AUTHORIZATION);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("profile", "正在读取你的账号角色", "当前用户");
        emit(emitter, started);
        try {
            CurrentUserProfile profile = agentSearchService.getCurrentUserProfile(userId, role, authorization);
            String roleName = profile == null ? "" : profile.roleName();
            emit(emitter, AgentSearchEvent.results(
                    started.searchId(),
                    "profile",
                    "已识别当前角色：" + roleName,
                    "当前用户",
                    List.of()));
            return profile;
        } catch (RuntimeException e) {
            emit(emitter, AgentSearchEvent.error(started.searchId(), "profile", "账号角色读取失败", "当前用户"));
            throw e;
        }
    }

    @Tool(name = "searchTeachingData", description = "Search authorized courses, chapters, question banks, questions, and live practices.")
    public List<AgentSearchItem> searchTeachingData(
            @ToolParam(description = "Keywords to search for.") String query,
            @ToolParam(description = "Optional course id to narrow the search.", required = false) String courseId,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        Integer role = intFromContext(toolContext, CONTEXT_USER_ROLE);
        UUID contextCourseId = uuidFromContext(toolContext, CONTEXT_COURSE_ID);
        UUID requestedCourseId = uuidValue(courseId);
        UUID effectiveCourseId = contextCourseId != null ? contextCourseId : requestedCourseId;
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("teaching", "正在检索课程内容", query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.searchTeachingData(query, effectiveCourseId, limit, userId, role);
            emit(emitter, AgentSearchEvent.results(
                    started.searchId(),
                    "teaching",
                    "找到 " + results.size() + " 条课程内容",
                    query,
                    results));
            return results;
        } catch (RuntimeException e) {
            emit(emitter, AgentSearchEvent.error(started.searchId(), "teaching", "课程内容检索失败", query));
            throw e;
        }
    }

    @Tool(name = "searchCourseResources", description = "Search authorized course resources by type: courses, chapters, question banks, questions, course files, live practices, and practice records.")
    public List<AgentSearchItem> searchCourseResources(
            @ToolParam(description = "Keywords to search for.") String query,
            @ToolParam(description = "Optional course title to narrow the search.", required = false) String courseTitle,
            @ToolParam(description = "Optional course id to narrow the search.", required = false) String courseId,
            @ToolParam(description = "Optional resource types: COURSE, CHAPTER, QUESTION_BANK, QUESTION, COURSE_FILE, LIVE_PRACTICE, PRACTICE_SESSION.", required = false) List<String> resourceTypes,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        Integer role = intFromContext(toolContext, CONTEXT_USER_ROLE);
        UUID contextCourseId = uuidFromContext(toolContext, CONTEXT_COURSE_ID);
        UUID requestedCourseId = uuidValue(courseId);
        UUID effectiveCourseId = contextCourseId != null ? contextCourseId : requestedCourseId;
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("resources", "正在检索课程资源", query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.searchCourseResources(
                    query, effectiveCourseId, courseTitle, resourceTypes, limit, userId, role);
            emit(emitter, AgentSearchEvent.results(
                    started.searchId(),
                    "resources",
                    "找到 " + results.size() + " 条课程资源",
                    query,
                    results));
            return results;
        } catch (RuntimeException e) {
            emit(emitter, AgentSearchEvent.error(started.searchId(), "resources", "课程资源检索失败", query));
            throw e;
        }
    }

    @Tool(name = "listMyCourses", description = "List current user's courses by scope: primaryTeaching, assisting, teaching, learning, or all.")
    public List<AgentSearchItem> listMyCourses(
            @ToolParam(description = "Course scope: primaryTeaching, assisting, teaching, learning, or all.", required = false) String scope,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        Integer role = intFromContext(toolContext, CONTEXT_USER_ROLE);
        String query = courseScopeLabel(scope);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("courses", "正在检索" + query, query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.listMyCourses(scope, limit, userId, role);
            emit(emitter, AgentSearchEvent.results(
                    started.searchId(),
                    "courses",
                    "找到 " + results.size() + " 门" + query,
                    query,
                    results));
            return results;
        } catch (RuntimeException e) {
            emit(emitter, AgentSearchEvent.error(started.searchId(), "courses", query + "检索失败", query));
            throw e;
        }
    }

    @Tool(name = "listCourseChapters", description = "List chapters or outline for an authorized course by course title or course id.")
    public List<AgentSearchItem> listCourseChapters(
            @ToolParam(description = "Course title to search within the current user's authorized courses.", required = false) String courseTitle,
            @ToolParam(description = "Optional concrete course id if already known.", required = false) String courseId,
            @ToolParam(description = "Maximum chapter count. Defaults to 20 and is capped at 50.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        Integer role = intFromContext(toolContext, CONTEXT_USER_ROLE);
        UUID contextCourseId = uuidFromContext(toolContext, CONTEXT_COURSE_ID);
        UUID requestedCourseId = uuidValue(courseId);
        UUID effectiveCourseId = contextCourseId != null ? contextCourseId : requestedCourseId;
        String query = effectiveCourseId != null ? effectiveCourseId.toString() : courseTitle;
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("chapters", "正在检索课程章节", query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.listCourseChapters(
                    courseTitle, effectiveCourseId, limit, userId, role);
            emit(emitter, AgentSearchEvent.results(
                    started.searchId(),
                    "chapters",
                    "找到 " + results.size() + " 个章节",
                    query,
                    results));
            return results;
        } catch (RuntimeException e) {
            emit(emitter, AgentSearchEvent.error(started.searchId(), "chapters", "课程章节检索失败", query));
            throw e;
        }
    }

    @Tool(name = "searchPersonalKnowledge", description = "Search the current user's personal knowledge documents.")
    public List<AgentSearchItem> searchPersonalKnowledge(
            @ToolParam(description = "Keywords to search for.") String query,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("knowledge", "正在检索个人知识库", query);
        emit(emitter, started);
        List<AgentSearchItem> results = agentSearchService.searchPersonalKnowledge(userId, query, limit);
        emit(emitter, AgentSearchEvent.results(
                started.searchId(),
                "knowledge",
                "找到 " + results.size() + " 条个人知识",
                query,
                results));
        return results;
    }

    @Tool(name = "searchChatMemory", description = "Search the current user's prior AI chat memory.")
    public List<AgentSearchItem> searchChatMemory(
            @ToolParam(description = "Keywords to search for.") String query,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("memory", "正在检索聊天记忆", query);
        emit(emitter, started);
        List<AgentSearchItem> results = agentSearchService.searchChatMemory(userId, query, limit);
        emit(emitter, AgentSearchEvent.results(
                started.searchId(),
                "memory",
                "找到 " + results.size() + " 条聊天记忆",
                query,
                results));
        return results;
    }

    @Tool(name = "queryPlatformApi", description = "Query a read-only platform API through the current user's permissions. Only GET endpoints listed in OpenAPI are allowed.")
    public AgentSearchItem queryPlatformApi(
            @ToolParam(description = "Platform service name: auth, course, storage, notification, or ai.") String service,
            @ToolParam(description = "Concrete API path, for example /api/auth/users/me or /api/courses.") String path,
            @ToolParam(description = "Optional query parameters for the GET request.", required = false) Map<String, Object> queryParams,
            ToolContext toolContext) {
        requireUserId(toolContext);
        String authorization = stringFromContext(toolContext, CONTEXT_AUTHORIZATION);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        String query = service + " " + path;
        AgentSearchEvent started = AgentSearchEvent.started("platform", "正在读取平台资料", query);
        emit(emitter, started);
        AgentSearchItem result = agentSearchService.queryPlatformApi(service, path, queryParams, authorization);
        if (result != null) {
            emit(emitter, AgentSearchEvent.results(started.searchId(), "platform", result.snippet(), query, List.of(result)));
        }
        return result;
    }

    private UUID requireUserId(ToolContext toolContext) {
        UUID userId = uuidFromContext(toolContext, CONTEXT_USER_ID);
        if (userId == null) {
            throw new IllegalStateException("AgentSearch tool context is missing userId");
        }
        return userId;
    }

    private UUID uuidFromContext(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        return uuidValue(toolContext.getContext().get(key));
    }

    private Integer intFromContext(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String stringFromContext(ToolContext toolContext, String key) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private AgentSearchEventEmitter eventEmitter(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(CONTEXT_EVENT_EMITTER);
        return value instanceof AgentSearchEventEmitter emitter ? emitter : null;
    }

    private void emit(AgentSearchEventEmitter emitter, AgentSearchEvent event) {
        if (emitter != null && event != null) {
            emitter.emit(event);
        }
    }

    private String courseScopeLabel(String scope) {
        if ("primaryTeaching".equals(scope)) {
            return "主讲课程";
        }
        if ("assisting".equals(scope)) {
            return "协助课程";
        }
        if ("learning".equals(scope)) {
            return "学习课程";
        }
        if ("all".equals(scope)) {
            return "可访问课程";
        }
        return "课程";
    }

    private UUID uuidValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
