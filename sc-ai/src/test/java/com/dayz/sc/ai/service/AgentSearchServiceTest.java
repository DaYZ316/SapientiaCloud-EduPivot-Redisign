package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.CourseAiContextClient;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.AgentSearchResult;
import com.dayz.sc.common.feign.dto.InternalUserProfile;
import com.dayz.sc.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSearchServiceTest {

    @Test
    void getCurrentDateTimeShouldReturnSystemTimeOutcome() {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDate before = LocalDate.now(zoneId);
        AgentSearchService service = serviceWith(mock(VectorStore.class));

        AgentSearchOutcome outcome = service.getCurrentDateTime();

        LocalDate after = LocalDate.now(zoneId);
        assertThat(outcome.status()).isEqualTo(AgentSearchStatus.OK);
        assertThat(outcome.domain()).isEqualTo("time");
        assertThat(outcome.provider()).isEqualTo("server-clock");
        assertThat(outcome.query()).isEqualTo("当前日期时间");
        assertThat(outcome.durationMs()).isNotNull();
        assertThat(outcome.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("SYSTEM_TIME");
            assertThat(item.sourceLabel()).isEqualTo("系统时间");
            assertThat(item.metadata())
                    .containsEntry("zoneId", "Asia/Shanghai")
                    .containsEntry("provider", "server-clock")
                    .containsKey("time")
                    .containsKey("instant")
                    .containsKey("weekday");
            assertThat(item.metadata().get("date")).isIn(before.toString(), after.toString());
            assertThat(item.sourceId()).isEqualTo(item.metadata().get("date"));
            assertThat(item.indexInfo()).containsEntry("zoneId", "Asia/Shanghai");
        });
    }

    @Test
    void searchPersonalKnowledgeShouldFilterByUserIdAndSourceType() {
        VectorStore vectorStore = mock(VectorStore.class);
        UUID docId = UUID.randomUUID();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document(
                "knowledge text",
                Map.of(KnowledgeBaseService.META_DOC_ID, docId.toString()))));
        AgentSearchService service = serviceWith(vectorStore);
        UUID userId = UUID.randomUUID();

        var results = service.searchPersonalKnowledge(userId, "knowledge", 99);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).sourceType()).isEqualTo("KNOWLEDGE_DOC");
        assertThat(results.get(0).sourceLabel()).isEqualTo("个人知识库");
        assertThat(results.get(0).title()).isEqualTo("个人知识库");
        assertThat(results.get(0).snippet()).isEqualTo("knowledge text");
        assertThat(results.get(0).metadata()).isEmpty();
        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                request.getTopK() == 10
                        && filterContains(request, userId.toString())
                        && filterContains(request, KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC)));
    }

    @Test
    void searchChatMemoryShouldFilterByUserIdAndSourceType() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        AgentSearchService service = serviceWith(vectorStore);
        UUID userId = UUID.randomUUID();

        service.searchChatMemory(userId, "history", 3);

        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                request.getTopK() == 3
                        && filterContains(request, userId.toString())
                        && filterContains(request, ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN)
                        && filterContains(request, ChatVectorMemoryService.META_DELETED)));
    }

    @Test
    void searchTeachingDataShouldSendServerUserContextHeaders() {
        CourseAiContextClient client = mock(CourseAiContextClient.class);
        AgentSearchResult result = new AgentSearchResult(
                "COURSE",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "AI Course",
                "snippet",
                Map.of("sourceLabel", "课程", "contextLabel", "AI Course", "relationLabel", "主讲课程"));
        when(client.search("AI", result.courseId(), 5, "9f1a8089-ff29-4756-b073-331f03404d75", "2"))
                .thenReturn(ApiResponse.ok(List.of(result)));
        AgentSearchService service = serviceWith(mock(VectorStore.class), client);

        var results = service.searchTeachingData(
                " AI ",
                result.courseId(),
                5,
                UUID.fromString("9f1a8089-ff29-4756-b073-331f03404d75"),
                2);

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("COURSE");
            assertThat(item.sourceLabel()).isEqualTo("课程");
            assertThat(item.title()).isEqualTo("AI Course");
            assertThat(item.contextLabel()).isEqualTo("AI Course");
            assertThat(item.snippet()).isEqualTo("snippet");
            assertThat(item.relationLabel()).isEqualTo("主讲课程");
            assertThat(item.metadata()).containsEntry("contextLabel", "AI Course");
            assertThat(item.sourceId()).isEqualTo(result.sourceId().toString());
            assertThat(item.courseId()).isEqualTo(result.courseId().toString());
            assertThat(item.indexInfo())
                    .containsEntry("sourceType", "COURSE")
                    .containsEntry("sourceId", result.sourceId().toString())
                    .containsEntry("courseId", result.courseId().toString())
                    .containsEntry("contextLabel", "AI Course");
        });
    }

    @Test
    void searchCourseResourcesShouldDelegateToCourseResourceEndpoint() {
        CourseAiContextClient client = mock(CourseAiContextClient.class);
        UUID courseId = UUID.randomUUID();
        UUID userId = UUID.fromString("9f1a8089-ff29-4756-b073-331f03404d75");
        AgentSearchResult result = new AgentSearchResult(
                "COURSE_FILE",
                UUID.randomUUID(),
                courseId,
                "slides.pdf",
                "slides.pdf",
                Map.of("sourceLabel", "课程文件", "contextLabel", "AI Course"));
        when(client.resources("slides", courseId, "AI Course", List.of("COURSE_FILE"), 5, userId.toString(), "2"))
                .thenReturn(ApiResponse.ok(List.of(result)));
        AgentSearchService service = serviceWith(mock(VectorStore.class), client);

        var results = service.searchCourseResources(" slides ", courseId, "AI Course",
                List.of("COURSE_FILE"), 5, userId, 2);

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.sourceType()).isEqualTo("COURSE_FILE");
            assertThat(item.sourceLabel()).isEqualTo("课程文件");
            assertThat(item.title()).isEqualTo("slides.pdf");
            assertThat(item.sourceId()).isEqualTo(result.sourceId().toString());
            assertThat(item.courseId()).isEqualTo(courseId.toString());
            assertThat(item.indexInfo())
                    .containsEntry("sourceType", "COURSE_FILE")
                    .containsEntry("sourceId", result.sourceId().toString())
                    .containsEntry("courseId", courseId.toString())
                    .containsEntry("contextLabel", "AI Course");
        });
    }

    @Test
    void searchTeachingDataShouldReturnEmptyWhenCourseSearchFails() {
        CourseAiContextClient client = mock(CourseAiContextClient.class);
        when(client.search(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"));
        AgentSearchService service = serviceWith(mock(VectorStore.class), client);

        var results = service.searchTeachingData("AI", null, 5, UUID.randomUUID(), 2);

        assertThat(results).isEmpty();
    }

    @Test
    void listCourseChaptersShouldSendServerUserContextHeaders() {
        CourseAiContextClient client = mock(CourseAiContextClient.class);
        UUID userId = UUID.fromString("9f1a8089-ff29-4756-b073-331f03404d75");
        AgentSearchItem chapter = new AgentSearchItem(
                "CHAPTER",
                "章节",
                "第一章 叙事原型",
                "中文游戏原型与关卡叙事实训",
                "章节介绍",
                "主讲课程",
                Map.of());
        when(client.chapters(null, "中文游戏原型与关卡叙事实训", 20, userId.toString(), "2"))
                .thenReturn(ApiResponse.ok(List.of(chapter)));
        AgentSearchService service = serviceWith(mock(VectorStore.class), client);

        var results = service.listCourseChapters("中文游戏原型与关卡叙事实训", null, null, userId, 2);

        assertThat(results).containsExactly(chapter);
    }

    @Test
    void getCurrentUserProfileShouldLoadReadableUserProfile() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        UUID userId = UUID.randomUUID();
        when(authClient.getUserProfile(userId)).thenReturn(ApiResponse.ok(
                new InternalUserProfile(userId, "liwenhao@edupivot.xyz", "Li Wenhao", "avatar.png", 2)));
        AgentSearchService service = serviceWith(mock(VectorStore.class), mock(CourseAiContextClient.class), authClient);

        var info = service.getCurrentUserProfile(userId, 2);

        assertThat(info.displayName()).isEqualTo("Li Wenhao");
        assertThat(info.accountName()).isEqualTo("Li Wenhao");
        assertThat(info.avatarUrl()).isEqualTo("avatar.png");
        assertThat(info.roleKey()).isEqualTo("TEACHER");
        assertThat(info.roleName()).isEqualTo("教师");
    }

    @Test
    void getCurrentUserProfileShouldUseAuthorizationBeforeInternalLookup() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        UUID userId = UUID.randomUUID();
        when(authClient.getCurrentUserProfile("Bearer token")).thenReturn(ApiResponse.ok(
                new InternalUserProfile(userId, "liwenhao@edupivot.xyz", "Li Wenhao", "avatar.png", 2)));
        AgentSearchService service = serviceWith(mock(VectorStore.class), mock(CourseAiContextClient.class), authClient);

        var info = service.getCurrentUserProfile(userId, 2, "Bearer token");

        assertThat(info.displayName()).isEqualTo("Li Wenhao");
        assertThat(info.accountName()).isEqualTo("Li Wenhao");
        assertThat(info.avatarUrl()).isEqualTo("avatar.png");
        assertThat(info.roleKey()).isEqualTo("TEACHER");
        verify(authClient, never()).getUserProfile(userId);
    }

    @Test
    void getCurrentUserProfileShouldFallbackToEmailAccountName() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        UUID userId = UUID.randomUUID();
        when(authClient.getUserProfile(userId)).thenReturn(ApiResponse.ok(
                new InternalUserProfile(userId, "liwenhao@edupivot.xyz", "", "avatar.png", 1)));
        AgentSearchService service = serviceWith(mock(VectorStore.class), mock(CourseAiContextClient.class), authClient);

        var info = service.getCurrentUserProfile(userId, 1);

        assertThat(info.displayName()).isNull();
        assertThat(info.accountName()).isEqualTo("liwenhao");
        assertThat(info.roleKey()).isEqualTo("STUDENT");
        assertThat(info.roleName()).isEqualTo("学生");
    }

    @Test
    void getCurrentUserProfileShouldUseJwtRoleWhenAuthRoleDiffers() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        UUID userId = UUID.randomUUID();
        when(authClient.getUserProfile(userId)).thenReturn(ApiResponse.ok(
                new InternalUserProfile(userId, "teacher@edupivot.xyz", "Teacher", "avatar.png", 1)));
        AgentSearchService service = serviceWith(mock(VectorStore.class), mock(CourseAiContextClient.class), authClient);

        var info = service.getCurrentUserProfile(userId, 2);

        assertThat(info.roleKey()).isEqualTo("TEACHER");
        assertThat(info.roleName()).isEqualTo("教师");
    }

    @Test
    void getCurrentUserProfileShouldFallbackToToolContextWhenAuthFails() {
        AuthInternalClient authClient = mock(AuthInternalClient.class);
        UUID userId = UUID.randomUUID();
        when(authClient.getUserProfile(userId)).thenThrow(new IllegalStateException("boom"));
        AgentSearchService service = serviceWith(mock(VectorStore.class), mock(CourseAiContextClient.class), authClient);

        var info = service.getCurrentUserProfile(userId, 1);

        assertThat(info.displayName()).isNull();
        assertThat(info.accountName()).isNull();
        assertThat(info.roleKey()).isEqualTo("STUDENT");
        assertThat(info.roleName()).isEqualTo("学生");
    }

    @Test
    void queryPlatformApiShouldDelegateWithAuthorization() {
        PlatformApiSearchClient platformClient = mock(PlatformApiSearchClient.class);
        AgentSearchItem item = new AgentSearchItem(
                "PLATFORM_API", "平台接口", "Current user", "auth", "{}", "", Map.of());
        when(platformClient.query("auth", "/api/auth/users/me", Map.of("page", 1), "Bearer token"))
                .thenReturn(item);
        AgentSearchService service = serviceWith(
                mock(VectorStore.class),
                mock(CourseAiContextClient.class),
                mock(AuthInternalClient.class),
                platformClient);

        var result = service.queryPlatformApi("auth", "/api/auth/users/me", Map.of("page", 1), "Bearer token");

        assertThat(result).isEqualTo(item);
    }

    @Test
    void searchWebShouldDelegateWithoutUserContext() {
        WebSearchClient webSearchClient = mock(WebSearchClient.class);
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
        when(webSearchClient.search("AI news", 3)).thenReturn(AgentSearchOutcome.ok(
                "web",
                "tavily-compatible",
                "AI news",
                "找到 1 条网页结果",
                12L,
                List.of(item)));
        AgentSearchService service = serviceWith(
                mock(VectorStore.class),
                mock(CourseAiContextClient.class),
                mock(AuthInternalClient.class),
                mock(PlatformApiSearchClient.class),
                webSearchClient);

        var results = service.searchWeb("AI news", 3);

        assertThat(results.status()).isEqualTo(AgentSearchStatus.OK);
        assertThat(results.items()).containsExactly(item);
        verify(webSearchClient).search("AI news", 3);
    }

    @SuppressWarnings("unchecked")
    private AgentSearchService serviceWith(VectorStore vectorStore) {
        return serviceWith(vectorStore, mock(CourseAiContextClient.class));
    }

    @SuppressWarnings("unchecked")
    private AgentSearchService serviceWith(VectorStore vectorStore, CourseAiContextClient client) {
        return serviceWith(vectorStore, client, mock(AuthInternalClient.class));
    }

    @SuppressWarnings("unchecked")
    private AgentSearchService serviceWith(VectorStore vectorStore,
                                           CourseAiContextClient client,
                                           AuthInternalClient authClient) {
        return serviceWith(vectorStore, client, authClient, mock(PlatformApiSearchClient.class));
    }

    @SuppressWarnings("unchecked")
    private AgentSearchService serviceWith(VectorStore vectorStore,
                                           CourseAiContextClient client,
                                           AuthInternalClient authClient,
                                           PlatformApiSearchClient platformClient) {
        return serviceWith(vectorStore, client, authClient, platformClient, mock(WebSearchClient.class));
    }

    @SuppressWarnings("unchecked")
    private AgentSearchService serviceWith(VectorStore vectorStore,
                                           CourseAiContextClient client,
                                           AuthInternalClient authClient,
                                           PlatformApiSearchClient platformClient,
                                           WebSearchClient webSearchClient) {
        ObjectProvider<@org.jspecify.annotations.NonNull VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(vectorStore);
        ChatVectorMemoryService memoryService = mock(ChatVectorMemoryService.class);
        when(memoryService.activeMemoryDocuments(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        return new AgentSearchService(
                client,
                authClient,
                platformClient,
                webSearchClient,
                provider,
                memoryService,
                new AiProperties(),
                new AiProviderCallGuard());
    }

    private boolean filterContains(SearchRequest request, String value) {
        return request.getFilterExpression() != null
                && request.getFilterExpression().toString().contains(value);
    }
}
