package com.dayz.sc.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import com.dayz.sc.common.feign.dto.AgentSearchItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
