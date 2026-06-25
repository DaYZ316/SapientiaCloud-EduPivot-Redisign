package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.LiveSummarySession;
import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;
import com.dayz.sc.ai.model.entity.LiveTranscriptSegment;
import com.dayz.sc.ai.model.enums.LiveSummaryStatus;
import com.dayz.sc.ai.model.vo.LiveSummaryAudioTokenVO;
import com.dayz.sc.ai.model.vo.LiveSummarySessionVO;
import com.dayz.sc.ai.model.vo.LiveSummarySnapshotVO;
import com.dayz.sc.ai.model.vo.LiveTranscriptSegmentVO;
import com.dayz.sc.ai.repository.LiveSummarySessionRepository;
import com.dayz.sc.ai.repository.LiveSummarySnapshotRepository;
import com.dayz.sc.ai.repository.LiveTranscriptSegmentRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.CourseAiContextClient;
import com.dayz.sc.common.feign.dto.ClassSessionAiAccess;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.util.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class LiveSummaryService {

    private static final String UNAVAILABLE_MESSAGE = "AI summary is temporarily unavailable.";
    private static final int SUCCESS_CODE = 0;

    private final LiveSummarySessionRepository sessionRepository;
    private final LiveTranscriptSegmentRepository transcriptRepository;
    private final LiveSummarySnapshotRepository snapshotRepository;
    private final CourseAiContextClient courseAiContextClient;
    private final LiveSummaryAudioTokenService audioTokenService;
    private final LiveSummaryEventHub eventHub;
    private final DashScopeAsrClient asrClient;
    private final ChatClient chatClient;
    private final AiRuntimeGuard aiRuntimeGuard;
    private final AiProviderCallGuard aiProviderCallGuard;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final ExecutorService summaryExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, ReentrantLock> summaryLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> lastSummaryAtBySession = new ConcurrentHashMap<>();

    public LiveSummaryService(LiveSummarySessionRepository sessionRepository,
                              LiveTranscriptSegmentRepository transcriptRepository,
                              LiveSummarySnapshotRepository snapshotRepository,
                              CourseAiContextClient courseAiContextClient,
                              LiveSummaryAudioTokenService audioTokenService,
                              LiveSummaryEventHub eventHub,
                              DashScopeAsrClient asrClient,
                              ChatClient chatClient,
                              AiRuntimeGuard aiRuntimeGuard,
                              AiProviderCallGuard aiProviderCallGuard,
                              AiProperties aiProperties,
                              ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.transcriptRepository = transcriptRepository;
        this.snapshotRepository = snapshotRepository;
        this.courseAiContextClient = courseAiContextClient;
        this.audioTokenService = audioTokenService;
        this.eventHub = eventHub;
        this.asrClient = asrClient;
        this.chatClient = chatClient;
        this.aiRuntimeGuard = aiRuntimeGuard;
        this.aiProviderCallGuard = aiProviderCallGuard;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public LiveSummarySessionVO start(UUID classSessionId, UUID userId, Integer role) {
        ClassSessionAiAccess access = requireAccess(classSessionId, userId, role, true);
        requireAvailable();
        Optional<LiveSummarySession> running = sessionRepository.findRunningByClassSessionId(classSessionId);
        if (running.isPresent()) {
            return toVO(running.get());
        }

        Instant now = Instant.now();
        LiveSummarySession session = new LiveSummarySession();
        session.setId(UuidV7Generator.generate());
        session.setClassSessionId(classSessionId);
        session.setCourseId(access.courseId());
        session.setTeacherId(access.teacherId());
        session.setStartedBy(userId);
        session.setStatus(LiveSummaryStatus.RUNNING.name());
        session.setCompressedState("");
        session.setLastSummarizedSequenceNo(0);
        session.setStartedAt(now);
        sessionRepository.save(session);
        lastSummaryAtBySession.put(session.getId(), now);
        LiveSummarySessionVO vo = toVO(session);
        eventHub.emit(classSessionId, "status", vo);
        return vo;
    }

    public LiveSummarySessionVO stop(UUID classSessionId, UUID userId, Integer role) {
        requireAccess(classSessionId, userId, role, true);
        Optional<LiveSummarySession> running = sessionRepository.findRunningByClassSessionId(classSessionId);
        if (running.isEmpty()) {
            return get(classSessionId, userId, role);
        }

        LiveSummarySession session = running.get();
        runSummary(session.getId(), true);
        session = sessionRepository.findById(session.getId()).orElse(session);
        session.setStatus(LiveSummaryStatus.STOPPED.name());
        session.setStoppedAt(Instant.now());
        sessionRepository.update(session);
        lastSummaryAtBySession.remove(session.getId());
        LiveSummarySessionVO vo = toVO(session);
        eventHub.emit(classSessionId, "status", vo);
        return vo;
    }

    public LiveSummarySessionVO get(UUID classSessionId, UUID userId, Integer role) {
        ClassSessionAiAccess access = requireAccess(classSessionId, userId, role, false);
        return sessionRepository.findLatestByClassSessionId(classSessionId)
                .map(this::toVO)
                .orElseGet(() -> emptyVO(access));
    }

    public Flux<ServerSentEvent<String>> stream(UUID classSessionId, UUID userId, Integer role) {
        LiveSummarySessionVO snapshot = get(classSessionId, userId, role);
        List<ServerSentEvent<String>> initialEvents = new ArrayList<>();
        initialEvents.add(eventHub.event("status", snapshot));
        if (snapshot.latestSnapshot() != null) {
            initialEvents.add(eventHub.event("summary_snapshot", snapshot.latestSnapshot()));
        }
        snapshot.recentTranscripts().forEach(segment ->
                initialEvents.add(eventHub.event("transcript", transcriptPayload(segment, true))));

        Flux<ServerSentEvent<String>> liveEvents = eventHub.stream(classSessionId);
        Flux<ServerSentEvent<String>> keepaliveEvents = Flux.interval(Duration.ofSeconds(15))
                .map(ignored -> eventHub.keepalive());
        return Flux.fromIterable(initialEvents)
                .concatWith(Flux.merge(liveEvents, keepaliveEvents));
    }

    public LiveSummaryAudioTokenVO issueAudioToken(UUID classSessionId, UUID userId, Integer role) {
        requireAccess(classSessionId, userId, role, true);
        LiveSummarySession session = sessionRepository.findRunningByClassSessionId(classSessionId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.BAD_REQUEST, "Live summary is not running."));
        return audioTokenService.issueToken(classSessionId, session.getId(), userId, role);
    }

    public DashScopeAsrClient.LiveSummaryAsrStream openAudioStream(UUID classSessionId,
                                                                   UUID summarySessionId,
                                                                   UUID userId,
                                                                   Integer role) {
        requireAccess(classSessionId, userId, role, true);
        LiveSummarySession session = sessionRepository.findById(summarySessionId)
                .filter(candidate -> classSessionId.equals(candidate.getClassSessionId()))
                .filter(candidate -> LiveSummaryStatus.RUNNING.name().equals(candidate.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCodes.BAD_REQUEST, "Live summary is not running."));
        return asrClient.connect(new DashScopeAsrClient.AsrListener() {
            @Override
            public void onTranscript(DashScopeAsrClient.AsrTranscript transcript) {
                if (transcript.sentenceEnd()) {
                    persistFinalTranscript(session.getId(), classSessionId, userId, transcript);
                } else {
                    emitPartialTranscript(session.getId(), classSessionId, userId, transcript);
                }
            }

            @Override
            public void onError(Throwable error) {
                failSession(session.getId(), classSessionId, "Speech recognition failed.");
            }
        });
    }

    private void persistFinalTranscript(UUID summarySessionId,
                                        UUID classSessionId,
                                        UUID speakerId,
                                        DashScopeAsrClient.AsrTranscript transcript) {
        if (!StringUtils.hasText(transcript.text())) {
            return;
        }
        LiveSummarySession session = sessionRepository.findById(summarySessionId)
                .filter(candidate -> LiveSummaryStatus.RUNNING.name().equals(candidate.getStatus()))
                .orElse(null);
        if (session == null) {
            return;
        }
        LiveTranscriptSegment segment = new LiveTranscriptSegment();
        segment.setId(UuidV7Generator.generate());
        segment.setSummarySessionId(summarySessionId);
        segment.setClassSessionId(classSessionId);
        segment.setSequenceNo(transcriptRepository.nextSequenceNo(summarySessionId));
        segment.setSpeakerId(speakerId);
        segment.setText(transcript.text().strip());
        segment.setBeginTimeMs(transcript.beginTimeMs());
        segment.setEndTimeMs(transcript.endTimeMs());
        transcriptRepository.save(segment);
        eventHub.emit(classSessionId, "transcript", transcriptPayload(toVO(segment), true));
        scheduleSummary(summarySessionId);
    }

    private void emitPartialTranscript(UUID summarySessionId,
                                       UUID classSessionId,
                                       UUID speakerId,
                                       DashScopeAsrClient.AsrTranscript transcript) {
        if (!StringUtils.hasText(transcript.text())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", null);
        payload.put("summarySessionId", summarySessionId);
        payload.put("classSessionId", classSessionId);
        payload.put("sequenceNo", null);
        payload.put("speakerId", speakerId);
        payload.put("text", transcript.text());
        payload.put("beginTimeMs", transcript.beginTimeMs());
        payload.put("endTimeMs", transcript.endTimeMs());
        payload.put("createdAt", Instant.now());
        payload.put("final", false);
        eventHub.emit(classSessionId, "transcript", payload);
    }

    private void scheduleSummary(UUID summarySessionId) {
        CompletableFuture.runAsync(() -> runSummary(summarySessionId, false), summaryExecutor)
                .exceptionally(error -> {
                    log.warn("Live summary generation failed", error);
                    return null;
                });
    }

    private void runSummary(UUID summarySessionId, boolean force) {
        ReentrantLock lock = summaryLocks.computeIfAbsent(summarySessionId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            return;
        }
        try {
            summarize(summarySessionId, force);
        } finally {
            lock.unlock();
        }
    }

    private void summarize(UUID summarySessionId, boolean force) {
        LiveSummarySession session = sessionRepository.findById(summarySessionId).orElse(null);
        if (session == null) {
            return;
        }
        int lastSummarizedSequence = session.getLastSummarizedSequenceNo() == null
                ? 0
                : session.getLastSummarizedSequenceNo();
        List<LiveTranscriptSegment> newSegments = transcriptRepository.findAfterSequence(
                summarySessionId, lastSummarizedSequence);
        if (newSegments.isEmpty()) {
            return;
        }
        if (!force && !shouldSummarize(session, newSegments)) {
            return;
        }

        LiveSummarySnapshot snapshot = createSnapshot(session, newSegments);
        snapshotRepository.save(snapshot);
        session.setCompressedState(compressedState(snapshot.getPayload()));
        session.setLastSummarizedSequenceNo(snapshot.getTranscriptUntilSequenceNo());
        sessionRepository.update(session);
        lastSummaryAtBySession.put(summarySessionId, Instant.now());
        eventHub.emit(session.getClassSessionId(), "summary_snapshot", toVO(snapshot));
        eventHub.emit(session.getClassSessionId(), "status", toVO(session));
    }

    private boolean shouldSummarize(LiveSummarySession session, List<LiveTranscriptSegment> newSegments) {
        int newChars = newSegments.stream()
                .map(LiveTranscriptSegment::getText)
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .sum();
        if (newChars >= aiProperties.getLiveSummary().getSummaryMinNewChars()) {
            return true;
        }
        Instant lastSummaryAt = latestSnapshot(session.getId())
                .map(LiveSummarySnapshot::getCreatedAt)
                .orElseGet(() -> lastSummaryAtBySession.getOrDefault(session.getId(), session.getStartedAt()));
        return lastSummaryAt == null
                || Duration.between(lastSummaryAt, Instant.now())
                .compareTo(aiProperties.getLiveSummary().getSummaryInterval()) >= 0;
    }

    private LiveSummarySnapshot createSnapshot(LiveSummarySession session, List<LiveTranscriptSegment> newSegments) {
        Map<String, Object> payload;
        try {
            payload = generateSummaryPayload(session.getCompressedState(), newSegments);
        } catch (RuntimeException exception) {
            log.warn("Live summary model call failed summarySessionId={}", session.getId(), exception);
            payload = fallbackPayload(session.getCompressedState(), newSegments);
        }

        LiveTranscriptSegment lastSegment = newSegments.getLast();
        LiveSummarySnapshot snapshot = new LiveSummarySnapshot();
        snapshot.setId(UuidV7Generator.generate());
        snapshot.setSummarySessionId(session.getId());
        snapshot.setClassSessionId(session.getClassSessionId());
        snapshot.setSequenceNo(snapshotRepository.nextSequenceNo(session.getId()));
        snapshot.setTranscriptUntilSequenceNo(lastSegment.getSequenceNo());
        snapshot.setOverview(stringValue(payload.get("overview")));
        snapshot.setPayload(payload);
        return snapshot;
    }

    private Map<String, Object> generateSummaryPayload(String compressedState, List<LiveTranscriptSegment> newSegments) {
        requireAvailable();
        String response = aiProviderCallGuard.call(() ->
                chatClient.prompt().user(summaryPrompt(compressedState, newSegments)).call().content());
        return parseSummaryPayload(response)
                .orElseGet(() -> fallbackPayload(compressedState, newSegments));
    }

    Optional<Map<String, Object>> parseSummaryPayload(String response) {
        if (!StringUtils.hasText(response)) {
            return Optional.empty();
        }
        String json = extractJsonObject(response);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(json, new TypeReference<>() {
            });
            return Optional.of(normalizePayload(payload));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private Map<String, Object> normalizePayload(Map<String, Object> payload) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        String overview = stringValue(payload.get("overview"));
        if (!StringUtils.hasText(overview)) {
            return fallbackPayload("", List.of());
        }
        normalized.put("overview", overview);
        normalized.put("keyPoints", listValue(payload.get("keyPoints")));
        normalized.put("timeline", listValue(payload.get("timeline")));
        normalized.put("questions", listValue(payload.get("questions")));
        Object mindMap = payload.get("mindMap");
        normalized.put("mindMap", mindMap instanceof Map<?, ?> ? mindMap : defaultMindMap(overview, normalized.get("keyPoints")));
        return normalized;
    }

    private String summaryPrompt(String compressedState, List<LiveTranscriptSegment> newSegments) {
        String state = StringUtils.hasText(compressedState) ? compressedState : "无";
        String transcript = truncate(joinTranscript(newSegments), aiProperties.getLiveSummary().getMaxIncrementChars());
        return """
                你是课堂直播的实时助教。请基于上一版压缩状态和新增教师转写，做增量课堂总结。

                要求：
                - 只总结教师讲授内容，不编造学生互动或课堂事实。
                - 保留可继续滚动压缩的知识结构。
                - 只返回 JSON，不要 Markdown，不要解释。
                - JSON 字段必须是：overview、keyPoints、timeline、questions、mindMap。
                - keyPoints 是字符串数组。
                - timeline 是对象数组，每项包含 time、title、detail。
                - questions 是字符串数组，放学生可能需要复习的问题。
                - mindMap 是 ECharts tree 数据：{"name":"课堂总结","children":[...]}。

                上一版压缩状态：
                %s

                新增转写：
                %s
                """.formatted(state, transcript);
    }

    private String joinTranscript(List<LiveTranscriptSegment> segments) {
        StringBuilder builder = new StringBuilder();
        for (LiveTranscriptSegment segment : segments) {
            builder.append(timeLabel(segment.getBeginTimeMs()))
                    .append(" ")
                    .append(segment.getText())
                    .append('\n');
        }
        return builder.toString();
    }

    private Map<String, Object> fallbackPayload(String compressedState, List<LiveTranscriptSegment> newSegments) {
        List<String> keyPoints = newSegments.stream()
                .map(LiveTranscriptSegment::getText)
                .filter(StringUtils::hasText)
                .map(text -> truncate(text.strip(), 80))
                .limit(5)
                .toList();
        String overview = keyPoints.isEmpty()
                ? stringValue(compressedState)
                : truncate(String.join("；", keyPoints), 240);
        if (!StringUtils.hasText(overview)) {
            overview = "课堂内容正在积累，等待更多有效转写。";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("overview", overview);
        payload.put("keyPoints", keyPoints);
        payload.put("timeline", newSegments.stream()
                .limit(6)
                .map(segment -> Map.of(
                        "time", timeLabel(segment.getBeginTimeMs()),
                        "title", truncate(segment.getText(), 32),
                        "detail", truncate(segment.getText(), 120)))
                .toList());
        payload.put("questions", List.of());
        payload.put("mindMap", defaultMindMap(overview, keyPoints));
        return payload;
    }

    private Map<String, Object> defaultMindMap(String overview, Object keyPoints) {
        List<?> points = keyPoints instanceof List<?> list ? list : List.of();
        List<Map<String, Object>> children = points.stream()
                .map(point -> {
                    Map<String, Object> child = new LinkedHashMap<>();
                    child.put("name", truncate(String.valueOf(point), 32));
                    return child;
                })
                .toList();
        if (children.isEmpty()) {
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("name", truncate(overview, 32));
            children = List.of(child);
        }
        return Map.of("name", "课堂总结", "children", children);
    }

    private String compressedState(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return stringValue(payload.get("overview"));
        }
    }

    private String extractJsonObject(String response) {
        String value = response.strip();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) {
            return "";
        }
        return value.substring(start, end + 1);
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(maxChars - 1, 0)) + "…";
    }

    private String timeLabel(Integer timeMs) {
        if (timeMs == null || timeMs < 0) {
            return "00:00";
        }
        int totalSeconds = timeMs / 1000;
        return "%02d:%02d".formatted(totalSeconds / 60, totalSeconds % 60);
    }

    private void failSession(UUID summarySessionId, UUID classSessionId, String message) {
        sessionRepository.findById(summarySessionId).ifPresent(session -> {
            if (LiveSummaryStatus.RUNNING.name().equals(session.getStatus())) {
                session.setStatus(LiveSummaryStatus.FAILED.name());
                session.setStoppedAt(Instant.now());
                sessionRepository.update(session);
                eventHub.emit(classSessionId, "status", toVO(session));
            }
        });
        eventHub.emitError(classSessionId, message);
    }

    private ClassSessionAiAccess requireAccess(UUID classSessionId, UUID userId, Integer role, boolean manage) {
        ClassSessionAiAccess access = access(classSessionId, userId, role);
        if (!access.canView() || (manage && !access.canManage())) {
            throw new BusinessException(ErrorCodes.FORBIDDEN);
        }
        return access;
    }

    private ClassSessionAiAccess access(UUID classSessionId, UUID userId, Integer role) {
        ApiResponse<ClassSessionAiAccess> response = courseAiContextClient.classSessionAccess(
                classSessionId,
                userId == null ? null : userId.toString(),
                role == null ? null : role.toString());
        if (response == null || response.code() != SUCCESS_CODE || response.data() == null) {
            throw new BusinessException(ErrorCodes.SERVICE_UNAVAILABLE);
        }
        return response.data();
    }

    private void requireAvailable() {
        if (!aiRuntimeGuard.isConfigured() || !asrClient.isConfigured()) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, UNAVAILABLE_MESSAGE);
        }
    }

    private Optional<LiveSummarySnapshot> latestSnapshot(UUID summarySessionId) {
        return snapshotRepository.findLatestBySummarySessionId(summarySessionId);
    }

    private LiveSummarySessionVO toVO(LiveSummarySession session) {
        return new LiveSummarySessionVO(
                session.getId(),
                session.getClassSessionId(),
                session.getCourseId(),
                session.getTeacherId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getStoppedAt(),
                latestSnapshot(session.getId()).map(this::toVO).orElse(null),
                transcriptRepository.findRecentBySummarySessionId(
                                session.getId(), aiProperties.getLiveSummary().getRecentTranscriptLimit())
                        .stream()
                        .map(this::toVO)
                        .toList());
    }

    private LiveSummarySessionVO emptyVO(ClassSessionAiAccess access) {
        return new LiveSummarySessionVO(
                null,
                access.classSessionId(),
                access.courseId(),
                access.teacherId(),
                "NOT_STARTED",
                null,
                null,
                null,
                List.of());
    }

    private LiveTranscriptSegmentVO toVO(LiveTranscriptSegment segment) {
        return new LiveTranscriptSegmentVO(
                segment.getId(),
                segment.getSummarySessionId(),
                segment.getClassSessionId(),
                segment.getSequenceNo(),
                segment.getSpeakerId(),
                segment.getText(),
                segment.getBeginTimeMs(),
                segment.getEndTimeMs(),
                segment.getCreatedAt());
    }

    private LiveSummarySnapshotVO toVO(LiveSummarySnapshot snapshot) {
        return new LiveSummarySnapshotVO(
                snapshot.getId(),
                snapshot.getSummarySessionId(),
                snapshot.getClassSessionId(),
                snapshot.getSequenceNo(),
                snapshot.getTranscriptUntilSequenceNo(),
                snapshot.getOverview(),
                snapshot.getPayload(),
                snapshot.getCreatedAt());
    }

    private Map<String, Object> transcriptPayload(LiveTranscriptSegmentVO segment, boolean isFinal) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", segment.id());
        payload.put("summarySessionId", segment.summarySessionId());
        payload.put("classSessionId", segment.classSessionId());
        payload.put("sequenceNo", segment.sequenceNo());
        payload.put("speakerId", segment.speakerId());
        payload.put("text", segment.text());
        payload.put("beginTimeMs", segment.beginTimeMs());
        payload.put("endTimeMs", segment.endTimeMs());
        payload.put("createdAt", segment.createdAt());
        payload.put("final", isFinal);
        return payload;
    }

    @PreDestroy
    void shutdown() {
        summaryExecutor.shutdownNow();
    }
}
