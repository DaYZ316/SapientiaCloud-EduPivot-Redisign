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

/**
 * AgentSearchTools.
 *
 * @author DaYZ
 */
@Component
@RequiredArgsConstructor
public class AgentSearchTools {

    public static final String CONTEXT_USER_ID = "userId";
    public static final String CONTEXT_USER_ROLE = "userRole";
    public static final String CONTEXT_COURSE_ID = "courseId";
    public static final String CONTEXT_AUTHORIZATION = "authorization";
    public static final String CONTEXT_EVENT_EMITTER = "agentSearchEventEmitter";
    public static final String CONTEXT_CURRENT_DATE_TIME_OUTCOME = "currentDateTimeOutcome";

    private static final String SCOPE_PRIMARY_TEACHING = "primaryTeaching";
    private static final String SCOPE_ASSISTING = "assisting";
    private static final String SCOPE_LEARNING = "learning";
    private static final String SCOPE_ALL = "all";

    private final AgentSearchService agentSearchService;

    @Tool(name = "getCurrentUserProfile", description = "Get the current logged-in user's display name, account name, avatar, and readable role.")
    public CurrentUserProfile getCurrentUserProfile(ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        Integer role = intFromContext(toolContext, CONTEXT_USER_ROLE);
        String authorization = stringFromContext(toolContext, CONTEXT_AUTHORIZATION);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("profile", "姝ｅ湪璇诲彇浣犵殑璐﹀彿瑙掕壊", "褰撳墠鐢ㄦ埛");
        emit(emitter, started);
        try {
            CurrentUserProfile profile = agentSearchService.getCurrentUserProfile(userId, role, authorization);
            String roleName = profile == null ? "" : profile.roleName();
            emit(emitter, AgentSearchEvent.results(
                    started.searchId(),
                    "profile",
                    "宸茶瘑鍒綋鍓嶈鑹诧細" + roleName,
                    "褰撳墠鐢ㄦ埛",
                    List.of()));
            return profile;
        } catch (RuntimeException e) {
            emit(emitter, AgentSearchEvent.error(started.searchId(), "profile", "璐﹀彿瑙掕壊璇诲彇澶辫触", "褰撳墠鐢ㄦ埛"));
            throw e;
        }
    }

    @Tool(name = "searchTeachingData", description = "Search authorized courses, chapters, question banks, questions, and live practices.")
    public AgentSearchOutcome searchTeachingData(
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
        AgentSearchEvent started = AgentSearchEvent.started("teaching", "Searching course content", query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.searchTeachingData(query, effectiveCourseId, limit, userId, role);
            AgentSearchOutcome outcome = outcome("teaching", "platform", query, "Found " + results.size() + " course content items", results);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        } catch (RuntimeException e) {
            AgentSearchOutcome outcome = failed("teaching", "platform", query, "Course content search failed", "Course content search service error");
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        }
    }

    @Tool(name = "searchCourseResources", description = "Search authorized course resources by type: courses, chapters, question banks, questions, course files, live practices, and practice records.")
    public AgentSearchOutcome searchCourseResources(
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
        AgentSearchEvent started = AgentSearchEvent.started("resources", "Searching course resources", query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.searchCourseResources(
                    query, effectiveCourseId, courseTitle, resourceTypes, limit, userId, role);
            AgentSearchOutcome outcome = outcome("resources", "platform", query, "Found " + results.size() + " course resources", results);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        } catch (RuntimeException e) {
            AgentSearchOutcome outcome = failed("resources", "platform", query, "Course resource search failed", "Course resource search service error");
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        }
    }

    @Tool(name = "listMyCourses", description = "List current user's courses by scope: primaryTeaching, assisting, teaching, learning, or all.")
    public AgentSearchOutcome listMyCourses(
            @ToolParam(description = "Course scope: primaryTeaching, assisting, teaching, learning, or all.", required = false) String scope,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        Integer role = intFromContext(toolContext, CONTEXT_USER_ROLE);
        String query = courseScopeLabel(scope);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("courses", "Searching " + query, query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.listMyCourses(scope, limit, userId, role);
            AgentSearchOutcome outcome = outcome("courses", "platform", query, "Found " + results.size() + " " + query, results);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        } catch (RuntimeException e) {
            AgentSearchOutcome outcome = failed("courses", "platform", query, query + " search failed", "Course list search service error");
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        }
    }

    @Tool(name = "listCourseChapters", description = "List chapters or outline for an authorized course by course title or course id.")
    public AgentSearchOutcome listCourseChapters(
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
        AgentSearchEvent started = AgentSearchEvent.started("chapters", "Searching course chapters", query);
        emit(emitter, started);
        try {
            List<AgentSearchItem> results = agentSearchService.listCourseChapters(
                    courseTitle, effectiveCourseId, limit, userId, role);
            AgentSearchOutcome outcome = outcome("chapters", "platform", query, "Found " + results.size() + " chapters", results);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        } catch (RuntimeException e) {
            AgentSearchOutcome outcome = failed("chapters", "platform", query, "Course chapter search failed", "Course chapter search service error");
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        }
    }

    @Tool(name = "searchPersonalKnowledge", description = "Search the current user's personal knowledge documents.")
    public AgentSearchOutcome searchPersonalKnowledge(
            @ToolParam(description = "Keywords to search for.") String query,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("knowledge", "姝ｅ湪妫€绱釜浜虹煡璇嗗簱", query);
        emit(emitter, started);
        List<AgentSearchItem> results = agentSearchService.searchPersonalKnowledge(userId, query, limit);
        AgentSearchOutcome outcome = outcome("knowledge", "vector-store", query, "Found " + results.size() + " personal knowledge items", results);
        emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
        return outcome;
    }

    @Tool(name = "searchChatMemory", description = "Search the current user's prior AI chat memory.")
    public AgentSearchOutcome searchChatMemory(
            @ToolParam(description = "Keywords to search for.") String query,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        UUID userId = requireUserId(toolContext);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("memory", "Searching chat memory", query);
        emit(emitter, started);
        List<AgentSearchItem> results = agentSearchService.searchChatMemory(userId, query, limit);
        AgentSearchOutcome outcome = outcome("memory", "vector-store", query, "Found " + results.size() + " chat memory items", results);
        emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
        return outcome;
    }

    @Tool(name = "searchWeb", description = "Search public web pages for current, recent, news, or external information. Use only when the user explicitly asks for web search or the answer depends on fresh public information.")
    public AgentSearchOutcome searchWeb(
            @ToolParam(description = "Public web search keywords.") String query,
            @ToolParam(description = "Maximum result count. Defaults to 5 and is capped at 10.", required = false) Integer limit,
            ToolContext toolContext) {
        requireUserId(toolContext);
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("web", "姝ｅ湪鑱旂綉鎼滅储", query);
        emit(emitter, started);
        try {
            AgentSearchOutcome outcome = agentSearchService.searchWeb(query, limit);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        } catch (RuntimeException e) {
            AgentSearchOutcome outcome = AgentSearchOutcome.failed(
                    "web",
                    "tavily-compatible",
                    query,
                    "鑱旂綉鎼滅储澶辫触",
                    "鑱旂綉鎼滅储宸ュ叿鎵ц寮傚父",
                    true,
                    null);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        }
    }

    @Tool(name = "getCurrentDateTime", description = "Read the current server date and time in Asia/Shanghai. Use before answering questions about today, now, latest, recent, news, this week, this month, or this year.")
    public AgentSearchOutcome getCurrentDateTime(ToolContext toolContext) {
        AgentSearchOutcome cachedOutcome = currentDateTimeOutcome(toolContext);
        if (cachedOutcome != null) {
            return cachedOutcome;
        }
        AgentSearchEventEmitter emitter = eventEmitter(toolContext);
        AgentSearchEvent started = AgentSearchEvent.started("time", "姝ｅ湪璇诲彇褰撳墠鏃ユ湡", "褰撳墠鏃ユ湡鏃堕棿");
        emit(emitter, started);
        try {
            AgentSearchOutcome outcome = agentSearchService.getCurrentDateTime();
            cacheCurrentDateTimeOutcome(toolContext, outcome);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        } catch (RuntimeException e) {
            AgentSearchOutcome outcome = AgentSearchOutcome.failed(
                    "time",
                    "server-clock",
                    "褰撳墠鏃ユ湡鏃堕棿",
                    "褰撳墠鏃ユ湡璇诲彇澶辫触",
                    "绯荤粺鏃堕棿宸ュ叿鎵ц寮傚父",
                    true,
                    null);
            cacheCurrentDateTimeOutcome(toolContext, outcome);
            emit(emitter, AgentSearchEvent.outcome(started.searchId(), outcome));
            return outcome;
        }
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
        AgentSearchEvent started = AgentSearchEvent.started("platform", "姝ｅ湪璇诲彇骞冲彴璧勬枡", query);
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
        if (toolContext == null) {
            return null;
        }
        return uuidValue(toolContext.getContext().get(key));
    }

    private Integer intFromContext(ToolContext toolContext, String key) {
        if (toolContext == null) {
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
        if (toolContext == null) {
            return null;
        }
        Object value = toolContext.getContext().get(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private AgentSearchEventEmitter eventEmitter(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object value = toolContext.getContext().get(CONTEXT_EVENT_EMITTER);
        return value instanceof AgentSearchEventEmitter emitter ? emitter : null;
    }

    private AgentSearchOutcome currentDateTimeOutcome(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object value = toolContext.getContext().get(CONTEXT_CURRENT_DATE_TIME_OUTCOME);
        return value instanceof AgentSearchOutcome outcome ? outcome : null;
    }

    private void cacheCurrentDateTimeOutcome(ToolContext toolContext, AgentSearchOutcome outcome) {
        if (toolContext == null || outcome == null) {
            return;
        }
        try {
            toolContext.getContext().put(CONTEXT_CURRENT_DATE_TIME_OUTCOME, outcome);
        } catch (UnsupportedOperationException ignored) {
            // Some callers pass immutable context maps; caching only avoids duplicate time reads.
        }
    }

    private void emit(AgentSearchEventEmitter emitter, AgentSearchEvent event) {
        if (emitter != null && event != null) {
            emitter.emit(event);
        }
    }

    private AgentSearchOutcome outcome(String domain,
                                       String provider,
                                       String query,
                                       String label,
                                       List<AgentSearchItem> items) {
        return AgentSearchOutcome.ok(domain, provider, query, label, null, items);
    }

    private AgentSearchOutcome failed(String domain, String provider, String query, String label, String reason) {
        return AgentSearchOutcome.failed(domain, provider, query, label, reason, true, null);
    }

    private String courseScopeLabel(String scope) {
        if (SCOPE_PRIMARY_TEACHING.equals(scope)) {
            return "涓昏璇剧▼";
        }
        if (SCOPE_ASSISTING.equals(scope)) {
            return "鍗忓姪璇剧▼";
        }
        if (SCOPE_LEARNING.equals(scope)) {
            return "瀛︿範璇剧▼";
        }
        if (SCOPE_ALL.equals(scope)) {
            return "all courses";
        }
        return "璇剧▼";
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
