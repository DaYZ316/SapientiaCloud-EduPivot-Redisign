package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.common.feign.client.AuthInternalClient;
import com.dayz.sc.common.feign.client.CourseAiContextClient;
import com.dayz.sc.common.feign.dto.AgentSearchItem;
import com.dayz.sc.common.feign.dto.AgentSearchResult;
import com.dayz.sc.common.feign.dto.CurrentUserProfile;
import com.dayz.sc.common.feign.dto.InternalUserProfile;
import com.dayz.sc.common.model.UserRole;
import com.dayz.sc.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSearchService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 10;
    private static final int MAX_SNIPPET_LENGTH = 240;

    private final CourseAiContextClient courseAiContextClient;
    private final AuthInternalClient authInternalClient;
    private final PlatformApiSearchClient platformApiSearchClient;
    private final ObjectProvider<@NonNull VectorStore> vectorStoreProvider;
    private final ChatVectorMemoryService chatVectorMemoryService;
    private final AiProperties aiProperties;
    private final AiProviderCallGuard aiProviderCallGuard;

    public CurrentUserProfile getCurrentUserProfile(UUID userId, Integer role) {
        return getCurrentUserProfile(userId, role, null);
    }

    public CurrentUserProfile getCurrentUserProfile(UUID userId, Integer role, String authorization) {
        if (userId == null) {
            return null;
        }
        CurrentUserProfile tokenProfile = getCurrentUserProfileWithToken(role, authorization);
        if (tokenProfile != null) {
            return tokenProfile;
        }
        try {
            ApiResponse<@NonNull InternalUserProfile> response = authInternalClient.getUserProfile(userId);
            if (response != null && response.code() == 0 && response.data() != null) {
                InternalUserProfile profile = response.data();
                Integer resolvedRole = role != null ? role : profile.role();
                String displayName = blankToNull(profile.displayName());
                return new CurrentUserProfile(
                        displayName,
                        accountName(displayName, profile.email()),
                        profile.avatarUrl(),
                        roleKey(resolvedRole),
                        roleName(resolvedRole));
            }
        } catch (RuntimeException e) {
            log.warn("AgentSearch current user lookup failed userId={}", userId, e);
        }
        return new CurrentUserProfile(null, null, null, roleKey(role), roleName(role));
    }

    public AgentSearchItem queryPlatformApi(String service,
                                            String path,
                                            Map<String, Object> queryParams,
                                            String authorization) {
        return platformApiSearchClient.query(service, path, queryParams, authorization);
    }

    public List<AgentSearchItem> listMyCourses(String scope, Integer limit, UUID userId, Integer role) {
        if (userId == null || role == null) {
            return List.of();
        }
        try {
            ApiResponse<@NonNull List<AgentSearchItem>> response = courseAiContextClient.courses(
                    scope,
                    normalizeLimit(limit),
                    userId.toString(),
                    role.toString());
            if (response == null || response.code() != 0 || response.data() == null) {
                return List.of();
            }
            return response.data();
        } catch (RuntimeException e) {
            log.warn("AgentSearch course list failed userId={} scope={}", userId, scope, e);
            return List.of();
        }
    }

    public List<AgentSearchItem> listCourseChapters(String courseTitle, UUID courseId, Integer limit, UUID userId, Integer role) {
        if ((courseId == null && !StringUtils.hasText(courseTitle)) || userId == null || role == null) {
            return List.of();
        }
        try {
            ApiResponse<@NonNull List<AgentSearchItem>> response = courseAiContextClient.chapters(
                    courseId,
                    courseTitle,
                    normalizeChapterLimit(limit),
                    userId.toString(),
                    role.toString());
            if (response == null || response.code() != 0 || response.data() == null) {
                return List.of();
            }
            return response.data();
        } catch (RuntimeException e) {
            log.warn("AgentSearch chapter list failed userId={} courseId={} courseTitle={}", userId, courseId, courseTitle, e);
            return List.of();
        }
    }

    public List<AgentSearchItem> searchTeachingData(String query, UUID courseId, Integer limit, UUID userId, Integer role) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        if (userId == null || role == null) {
            return List.of();
        }
        try {
            ApiResponse<@NonNull List<AgentSearchResult>> response =
                    courseAiContextClient.search(
                            query.strip(),
                            courseId,
                            normalizeLimit(limit),
                            userId.toString(),
                            role.toString());
            if (response == null || response.code() != 0 || response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .filter(Objects::nonNull)
                    .map(this::toItem)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("AgentSearch teaching data search failed userId={} courseId={}", userId, courseId, e);
            return List.of();
        }
    }

    public List<AgentSearchItem> searchCourseResources(String query,
                                                       UUID courseId,
                                                       String courseTitle,
                                                       List<String> resourceTypes,
                                                       Integer limit,
                                                       UUID userId,
                                                       Integer role) {
        if (!StringUtils.hasText(query) || userId == null || role == null) {
            return List.of();
        }
        try {
            ApiResponse<@NonNull List<AgentSearchResult>> response =
                    courseAiContextClient.resources(
                            query.strip(),
                            courseId,
                            courseTitle,
                            resourceTypes,
                            normalizeLimit(limit),
                            userId.toString(),
                            role.toString());
            if (response == null || response.code() != 0 || response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .filter(Objects::nonNull)
                    .map(this::toItem)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("AgentSearch course resource search failed userId={} courseId={} courseTitle={}",
                    userId, courseId, courseTitle, e);
            return List.of();
        }
    }

    public List<AgentSearchItem> searchPersonalKnowledge(UUID userId, String query, Integer limit) {
        return searchVectorDocs(
                userId,
                query,
                limit,
                KnowledgeBaseService.META_SOURCE_TYPE_KNOWLEDGE_DOC,
                "KNOWLEDGE_DOC",
                aiProperties.getRag().getSimilarityThreshold());
    }

    public List<AgentSearchItem> searchChatMemory(UUID userId, String query, Integer limit) {
        if (!aiProperties.getChatVectorMemory().isEnabled()) {
            return List.of();
        }
        return searchVectorDocs(
                userId,
                query,
                limit,
                ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN,
                "CHAT_MEMORY",
                aiProperties.getChatVectorMemory().getSimilarityThreshold(),
                ChatVectorMemoryService.META_DELETED + " == 'false'");
    }

    private List<AgentSearchItem> searchVectorDocs(UUID userId,
                                                   String query,
                                                   Integer limit,
                                                   String sourceType,
                                                   String resultType,
                                                   double similarityThreshold) {
        return searchVectorDocs(userId, query, limit, sourceType, resultType, similarityThreshold, null);
    }

    private List<AgentSearchItem> searchVectorDocs(UUID userId,
                                                   String query,
                                                   Integer limit,
                                                   String sourceType,
                                                   String resultType,
                                                   double similarityThreshold,
                                                   String extraFilter) {
        if (userId == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        int topK = normalizeLimit(limit);
        SearchRequest request = SearchRequest.builder()
                .query(query.strip())
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(sourceFilter(userId, sourceType, extraFilter))
                .build();
        try {
            List<Document> docs = aiProviderCallGuard.call(() -> vectorStoreProvider.getObject().similaritySearch(request));
            if (docs == null || docs.isEmpty()) {
                return List.of();
            }
            if (ChatVectorMemoryService.META_SOURCE_TYPE_CHAT_TURN.equals(sourceType)) {
                docs = chatVectorMemoryService.activeMemoryDocuments(userId, docs);
            }
            return docs.stream()
                    .filter(Objects::nonNull)
                    .limit(topK)
                    .map(document -> toItem(document, resultType))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("AgentSearch vector search failed sourceType={} userId={}", sourceType, userId, e);
            return List.of();
        }
    }

    private AgentSearchItem toItem(AgentSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        return new AgentSearchItem(
                result.sourceType(),
                textMetadata(metadata, "sourceLabel", sourceLabel(result.sourceType())),
                result.sourceId() == null ? null : result.sourceId().toString(),
                result.courseId() == null ? null : result.courseId().toString(),
                result.title(),
                textMetadata(metadata, "contextLabel", ""),
                result.snippet(),
                textMetadata(metadata, "relationLabel", ""),
                metadata,
                indexInfo(result));
    }

    private AgentSearchItem toItem(Document document, String resultType) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        return new AgentSearchItem(
                resultType,
                sourceLabel(resultType),
                textMetadata(metadata, KnowledgeBaseService.META_DOC_ID, null),
                null,
                sourceLabel(resultType),
                "",
                snippet(document.getText()),
                "",
                Map.of(),
                metadata);
    }

    private Map<String, Object> indexInfo(AgentSearchResult result) {
        Map<String, Object> indexInfo = new java.util.LinkedHashMap<>();
        indexInfo.put("sourceType", result.sourceType());
        if (result.sourceId() != null) {
            indexInfo.put("sourceId", result.sourceId().toString());
        }
        if (result.courseId() != null) {
            indexInfo.put("courseId", result.courseId().toString());
        }
        if (result.metadata() != null) {
            indexInfo.putAll(result.metadata());
        }
        return indexInfo;
    }

    private CurrentUserProfile getCurrentUserProfileWithToken(Integer role, String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        try {
            ApiResponse<@NonNull InternalUserProfile> response = authInternalClient.getCurrentUserProfile(authorization);
            if (response != null && response.code() == 0 && response.data() != null) {
                InternalUserProfile profile = response.data();
                Integer resolvedRole = role != null ? role : profile.role();
                String displayName = blankToNull(profile.displayName());
                return new CurrentUserProfile(
                        displayName,
                        accountName(displayName, profile.email()),
                        profile.avatarUrl(),
                        roleKey(resolvedRole),
                        roleName(resolvedRole));
            }
        } catch (RuntimeException e) {
            log.warn("AgentSearch current user token lookup failed", e);
        }
        return null;
    }

    private String snippet(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH);
    }

    private String sourceFilter(UUID userId, String sourceType) {
        return sourceFilter(userId, sourceType, null);
    }

    private String sourceFilter(UUID userId, String sourceType, String extraFilter) {
        String filter = "%s == '%s' && %s == '%s'"
                .formatted(KnowledgeBaseService.META_USER_ID, userId, KnowledgeBaseService.META_SOURCE_TYPE, sourceType);
        return StringUtils.hasText(extraFilter) ? filter + " && " + extraFilter : filter;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int normalizeChapterLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 50);
    }

    private String textMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private String sourceLabel(String sourceType) {
        return switch (sourceType) {
            case "COURSE" -> "课程";
            case "CHAPTER" -> "章节";
            case "QUESTION_BANK" -> "题库";
            case "QUESTION" -> "题目";
            case "COURSE_FILE" -> "课程文件";
            case "LIVE_PRACTICE" -> "课堂练习";
            case "PRACTICE_SESSION" -> "练习记录";
            case "KNOWLEDGE_DOC" -> "个人知识库";
            case "CHAT_MEMORY" -> "聊天记忆";
            default -> sourceType;
        };
    }

    private String accountName(String displayName, String email) {
        if (StringUtils.hasText(displayName)) {
            return displayName.strip();
        }
        if (!StringUtils.hasText(email)) {
            return null;
        }
        String normalized = email.strip();
        int atIndex = normalized.indexOf('@');
        return atIndex > 0 ? normalized.substring(0, atIndex) : normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private String roleKey(Integer role) {
        UserRole userRole = UserRole.fromCode(role);
        return userRole == null ? "" : userRole.name();
    }

    private String roleName(Integer role) {
        UserRole userRole = UserRole.fromCode(role);
        if (userRole == UserRole.ADMIN) {
            return "管理员";
        }
        if (userRole == UserRole.TEACHER) {
            return "教师";
        }
        if (userRole == UserRole.STUDENT) {
            return "学生";
        }
        return "";
    }
}
