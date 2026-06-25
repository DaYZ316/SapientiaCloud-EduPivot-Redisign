package com.dayz.sc.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.dayz.sc.common.feign.dto.AgentSearchItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSearchToolsTest {

    @Test
    void getCurrentUserProfileShouldUseUserIdAndRoleFromToolContext() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();

        tools.getCurrentUserProfile(new ToolContext(Map.of(
                AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                AgentSearchTools.CONTEXT_USER_ROLE, "1",
                AgentSearchTools.CONTEXT_AUTHORIZATION, "Bearer token")));

        verify(service).getCurrentUserProfile(userId, 1, "Bearer token");
    }

    @Test
    void listMyCoursesShouldUseScopeAndCurrentRoleFromToolContext() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();

        tools.listMyCourses("primaryTeaching", 3, new ToolContext(Map.of(
                AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                AgentSearchTools.CONTEXT_USER_ROLE, "2")));

        verify(service).listMyCourses("primaryTeaching", 3, userId, 2);
    }

    @Test
    void searchPersonalKnowledgeShouldUseUserIdFromToolContext() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();

        tools.searchPersonalKnowledge("vectors", 99,
                new ToolContext(Map.of(AgentSearchTools.CONTEXT_USER_ID, userId.toString())));

        verify(service).searchPersonalKnowledge(userId, "vectors", 99);
    }

    @Test
    void searchChatMemoryShouldRejectMissingToolContextUserId() {
        AgentSearchTools tools = new AgentSearchTools(mock(AgentSearchService.class));

        assertThatThrownBy(() -> tools.searchChatMemory("history", 5, new ToolContext(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void searchTeachingDataShouldPreferContextCourseId() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();
        UUID contextCourseId = UUID.randomUUID();
        UUID requestedCourseId = UUID.randomUUID();

        tools.searchTeachingData("lesson", requestedCourseId.toString(), 5,
                new ToolContext(Map.of(
                        AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                        AgentSearchTools.CONTEXT_USER_ROLE, "2",
                        AgentSearchTools.CONTEXT_COURSE_ID, contextCourseId.toString())));

        verify(service).searchTeachingData("lesson", contextCourseId, 5, userId, 2);
    }

    @Test
    void searchCourseResourcesShouldPreferContextCourseIdAndPassTypes() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();
        UUID contextCourseId = UUID.randomUUID();
        UUID requestedCourseId = UUID.randomUUID();
        List<String> resourceTypes = List.of("COURSE_FILE");

        tools.searchCourseResources("slides", "AI Course", requestedCourseId.toString(), resourceTypes, 5,
                new ToolContext(Map.of(
                        AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                        AgentSearchTools.CONTEXT_USER_ROLE, "2",
                        AgentSearchTools.CONTEXT_COURSE_ID, contextCourseId.toString())));

        verify(service).searchCourseResources("slides", contextCourseId, "AI Course", resourceTypes, 5, userId, 2);
    }

    @Test
    void listCourseChaptersShouldUseCourseTitleAndCurrentRoleFromToolContext() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();

        tools.listCourseChapters("中文游戏原型与关卡叙事实训", null, 20,
                new ToolContext(Map.of(
                        AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                        AgentSearchTools.CONTEXT_USER_ROLE, "2")));

        verify(service).listCourseChapters("中文游戏原型与关卡叙事实训", null, 20, userId, 2);
    }

    @Test
    void queryPlatformApiShouldUseAuthorizationFromToolContext() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();
        Map<String, Object> queryParams = Map.of("page", 1);
        AgentSearchItem item = new AgentSearchItem(
                "PLATFORM_API", "平台接口", "Current user", "auth", "{}", "", Map.of());
        org.mockito.Mockito.when(service.queryPlatformApi("auth", "/api/auth/users/me", queryParams, "Bearer token"))
                .thenReturn(item);

        tools.queryPlatformApi("auth", "/api/auth/users/me", queryParams,
                new ToolContext(Map.of(
                        AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                        AgentSearchTools.CONTEXT_AUTHORIZATION, "Bearer token")));

        verify(service).queryPlatformApi("auth", "/api/auth/users/me", queryParams, "Bearer token");
    }

    @Test
    void getCurrentDateTimeShouldEmitStartedAndResultsEvents() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        AgentSearchItem item = new AgentSearchItem(
                "SYSTEM_TIME",
                "系统时间",
                "2026-06-24",
                null,
                "当前日期：2026-06-24",
                "Asia/Shanghai",
                "当前日期是 2026-06-24，当前时间是 12:00:00，时区 Asia/Shanghai。",
                "系统时间",
                Map.of(
                        "date", "2026-06-24",
                        "time", "12:00:00",
                        "zoneId", "Asia/Shanghai",
                        "instant", "2026-06-24T04:00:00Z",
                        "weekday", "WEDNESDAY",
                        "provider", "server-clock"),
                Map.of("date", "2026-06-24", "zoneId", "Asia/Shanghai"));
        when(service.getCurrentDateTime()).thenReturn(AgentSearchOutcome.ok(
                "time",
                "server-clock",
                "当前日期时间",
                "已读取当前日期",
                1L,
                List.of(item)));
        List<AgentSearchEvent> events = new ArrayList<>();

        AgentSearchOutcome result = tools.getCurrentDateTime(new ToolContext(Map.of(
                AgentSearchTools.CONTEXT_EVENT_EMITTER, (AgentSearchEventEmitter) events::add)));

        assertThat(result.status()).isEqualTo(AgentSearchStatus.OK);
        assertThat(result.items()).containsExactly(item);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).phase()).isEqualTo("started");
        assertThat(events.get(0).domain()).isEqualTo("time");
        assertThat(events.get(0).label()).isNotBlank();
        assertThat(events.get(1).phase()).isEqualTo("results");
        assertThat(events.get(1).status()).isEqualTo(AgentSearchStatus.OK);
        assertThat(events.get(1).provider()).isEqualTo("server-clock");
        assertThat(events.get(1).items()).containsExactly(item);
        verify(service).getCurrentDateTime();
    }

    @Test
    void getCurrentDateTimeShouldReuseCachedOutcomeWithoutEmittingEvents() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        AgentSearchOutcome cachedOutcome = AgentSearchOutcome.ok(
                "time",
                "server-clock",
                "cached",
                "cached",
                1L,
                List.of());
        List<AgentSearchEvent> events = new ArrayList<>();

        AgentSearchOutcome result = tools.getCurrentDateTime(new ToolContext(Map.of(
                AgentSearchTools.CONTEXT_CURRENT_DATE_TIME_OUTCOME, cachedOutcome,
                AgentSearchTools.CONTEXT_EVENT_EMITTER, (AgentSearchEventEmitter) events::add)));

        assertThat(result).isSameAs(cachedOutcome);
        assertThat(events).isEmpty();
        verify(service, org.mockito.Mockito.never()).getCurrentDateTime();
    }

    @Test
    void searchWebShouldEmitStartedAndResultsEvents() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();
        AgentSearchItem item = new AgentSearchItem(
                "WEB_SEARCH",
                "网页",
                "https://example.com",
                null,
                "Example",
                "https://example.com",
                "Example snippet",
                "联网搜索",
                Map.of("url", "https://example.com"),
                Map.of("url", "https://example.com"));
        when(service.searchWeb("AI news", 2)).thenReturn(AgentSearchOutcome.ok(
                "web",
                "tavily-compatible",
                "AI news",
                "找到 1 条网页结果",
                15L,
                List.of(item)));
        List<AgentSearchEvent> events = new ArrayList<>();

        var results = tools.searchWeb("AI news", 2, new ToolContext(Map.of(
                AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                AgentSearchTools.CONTEXT_EVENT_EMITTER, (AgentSearchEventEmitter) events::add)));

        assertThat(results.status()).isEqualTo(AgentSearchStatus.OK);
        assertThat(results.items()).containsExactly(item);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).phase()).isEqualTo("started");
        assertThat(events.get(0).domain()).isEqualTo("web");
        assertThat(events.get(0).label()).isNotBlank();
        assertThat(events.get(1).phase()).isEqualTo("results");
        assertThat(events.get(1).label()).isEqualTo("找到 1 条网页结果");
        assertThat(events.get(1).items()).containsExactly(item);
        assertThat(events.get(1).status()).isEqualTo(AgentSearchStatus.OK);
        assertThat(events.get(1).provider()).isEqualTo("tavily-compatible");
        assertThat(events.get(1).durationMs()).isEqualTo(15L);
        verify(service).searchWeb("AI news", 2);
    }

    @Test
    void searchWebShouldEmitEmptyWhenNoResults() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();
        List<AgentSearchEvent> events = new ArrayList<>();
        when(service.searchWeb("AI news", 2)).thenReturn(AgentSearchOutcome.empty(
                "web",
                "tavily-compatible",
                "AI news",
                "未找到可引用网页结果",
                8L));

        var results = tools.searchWeb("AI news", 2, new ToolContext(Map.of(
                AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                AgentSearchTools.CONTEXT_EVENT_EMITTER, (AgentSearchEventEmitter) events::add)));

        assertThat(results.status()).isEqualTo(AgentSearchStatus.EMPTY);
        assertThat(results.items()).isEmpty();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).phase()).isEqualTo("empty");
        assertThat(events.get(1).label()).isEqualTo("未找到可引用网页结果");
        assertThat(events.get(1).status()).isEqualTo(AgentSearchStatus.EMPTY);
    }

    @Test
    void searchWebShouldEmitErrorAndReturnEmptyWhenServiceFails() {
        AgentSearchService service = mock(AgentSearchService.class);
        AgentSearchTools tools = new AgentSearchTools(service);
        UUID userId = UUID.randomUUID();
        when(service.searchWeb("AI news", 2)).thenReturn(AgentSearchOutcome.failed(
                "web",
                "tavily-compatible",
                "AI news",
                "联网搜索未配置",
                "缺少联网搜索 endpoint 或 api key",
                false,
                null));
        List<AgentSearchEvent> events = new ArrayList<>();

        var results = tools.searchWeb("AI news", 2, new ToolContext(Map.of(
                AgentSearchTools.CONTEXT_USER_ID, userId.toString(),
                AgentSearchTools.CONTEXT_EVENT_EMITTER, (AgentSearchEventEmitter) events::add)));

        assertThat(results.status()).isEqualTo(AgentSearchStatus.FAILED);
        assertThat(results.items()).isEmpty();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).phase()).isEqualTo("error");
        assertThat(events.get(1).label()).isEqualTo("联网搜索未配置");
        assertThat(events.get(1).reason()).isEqualTo("缺少联网搜索 endpoint 或 api key");
    }
}
