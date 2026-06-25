package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.LiveSummarySession;
import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;
import com.dayz.sc.ai.model.entity.LiveTranscriptSegment;
import com.dayz.sc.ai.model.enums.LiveSummaryStatus;
import com.dayz.sc.ai.model.vo.LiveSummarySessionVO;
import com.dayz.sc.ai.repository.LiveSummarySessionRepository;
import com.dayz.sc.ai.repository.LiveSummarySnapshotRepository;
import com.dayz.sc.ai.repository.LiveTranscriptSegmentRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.feign.client.CourseAiContextClient;
import com.dayz.sc.common.feign.dto.ClassSessionAiAccess;
import com.dayz.sc.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSummaryServiceTest {

    private final InMemoryLiveSummarySessionRepository sessionRepository = new InMemoryLiveSummarySessionRepository();
    private final InMemoryLiveTranscriptSegmentRepository transcriptRepository = new InMemoryLiveTranscriptSegmentRepository();
    private final InMemoryLiveSummarySnapshotRepository snapshotRepository = new InMemoryLiveSummarySnapshotRepository();
    private final CourseAiContextClient courseAiContextClient = mock(CourseAiContextClient.class);
    private final LiveSummaryAudioTokenService audioTokenService = mock(LiveSummaryAudioTokenService.class);
    private final LiveSummaryEventHub eventHub = new LiveSummaryEventHub(new ObjectMapper());
    private final DashScopeAsrClient asrClient = mock(DashScopeAsrClient.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final AiRuntimeGuard aiRuntimeGuard = mock(AiRuntimeGuard.class);
    private final AiProviderCallGuard aiProviderCallGuard = mock(AiProviderCallGuard.class);
    private final AiProperties aiProperties = new AiProperties();
    private final LiveSummaryService service = new LiveSummaryService(
            sessionRepository,
            transcriptRepository,
            snapshotRepository,
            courseAiContextClient,
            audioTokenService,
            eventHub,
            asrClient,
            chatClient,
            aiRuntimeGuard,
            aiProviderCallGuard,
            aiProperties,
            new ObjectMapper());

    @AfterEach
    void shutdown() {
        service.shutdown();
    }

    @Test
    void startShouldCreateRunningSessionForManager() {
        UUID classSessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        givenAiConfigured();

        LiveSummarySessionVO result = service.start(classSessionId, userId, 2);

        assertThat(result.status()).isEqualTo(LiveSummaryStatus.RUNNING.name());
        assertThat(result.classSessionId()).isEqualTo(classSessionId);
        assertThat(result.courseId()).isEqualTo(courseId);
        assertThat(result.teacherId()).isEqualTo(teacherId);
        assertThat(sessionRepository.sessions).hasSize(1);
        assertThat(sessionRepository.sessions.getFirst().getStartedBy()).isEqualTo(userId);
    }

    @Test
    void startShouldRejectViewerWithoutManageAccess() {
        UUID classSessionId = UUID.randomUUID();
        givenAccess(classSessionId, UUID.randomUUID(), UUID.randomUUID(), true, false);
        givenAiConfigured();

        assertThatThrownBy(() -> service.start(classSessionId, UUID.randomUUID(), 1))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void stopShouldPersistFallbackSnapshotWhenModelReturnsInvalidJson() {
        UUID classSessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        givenAccess(classSessionId, UUID.randomUUID(), UUID.randomUUID(), true, true);
        givenAiConfigured();
        stubModelResponse("not json");
        aiProperties.getLiveSummary().setSummaryInterval(Duration.ZERO);
        LiveSummarySessionVO started = service.start(classSessionId, userId, 2);
        UUID summarySessionId = started.id();
        transcriptRepository.save(segment(summarySessionId, classSessionId, userId, 1, "第一段课程重点"));
        transcriptRepository.save(segment(summarySessionId, classSessionId, userId, 2, "第二段知识结构"));

        LiveSummarySessionVO stopped = service.stop(classSessionId, userId, 2);

        assertThat(stopped.status()).isEqualTo(LiveSummaryStatus.STOPPED.name());
        assertThat(snapshotRepository.snapshots).hasSize(1);
        LiveSummarySnapshot snapshot = snapshotRepository.snapshots.getFirst();
        assertThat(snapshot.getTranscriptUntilSequenceNo()).isEqualTo(2);
        assertThat(snapshot.getOverview()).contains("第一段课程重点", "第二段知识结构");
        assertThat(snapshot.getPayload()).containsKeys("overview", "keyPoints", "timeline", "questions", "mindMap");
        verify(chatClient).prompt();
    }

    private void givenAiConfigured() {
        when(aiRuntimeGuard.isConfigured()).thenReturn(true);
        when(asrClient.isConfigured()).thenReturn(true);
    }

    private void givenAccess(UUID classSessionId, UUID courseId, UUID teacherId, boolean canView, boolean canManage) {
        when(courseAiContextClient.classSessionAccess(any(), any(), any())).thenReturn(ApiResponse.ok(
                new ClassSessionAiAccess(
                        classSessionId,
                        courseId,
                        teacherId,
                        "Live class",
                        1,
                        "published",
                        1,
                        "live",
                        Instant.now(),
                        Instant.now().plus(Duration.ofHours(1)),
                        canView,
                        canManage)));
    }

    private void stubModelResponse(String response) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class, RETURNS_SELF);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
        when(aiProviderCallGuard.call(any())).thenAnswer(invocation ->
                invocation.getArgument(0, Supplier.class).get());
    }

    private LiveTranscriptSegment segment(UUID summarySessionId,
                                          UUID classSessionId,
                                          UUID speakerId,
                                          int sequenceNo,
                                          String text) {
        LiveTranscriptSegment segment = new LiveTranscriptSegment();
        segment.setId(UUID.randomUUID());
        segment.setSummarySessionId(summarySessionId);
        segment.setClassSessionId(classSessionId);
        segment.setSequenceNo(sequenceNo);
        segment.setSpeakerId(speakerId);
        segment.setText(text);
        segment.setBeginTimeMs(sequenceNo * 1000);
        segment.setEndTimeMs(sequenceNo * 1000 + 900);
        segment.setCreatedAt(Instant.now());
        return segment;
    }

    private static class InMemoryLiveSummarySessionRepository implements LiveSummarySessionRepository {

        private final List<LiveSummarySession> sessions = new ArrayList<>();

        @Override
        public void save(LiveSummarySession session) {
            sessions.add(session);
        }

        @Override
        public void update(LiveSummarySession session) {
            // Entities are kept by reference in this in-memory repository.
        }

        @Override
        public Optional<LiveSummarySession> findById(UUID id) {
            return sessions.stream()
                    .filter(session -> session.getId().equals(id))
                    .findFirst();
        }

        @Override
        public Optional<LiveSummarySession> findLatestByClassSessionId(UUID classSessionId) {
            return sessions.stream()
                    .filter(session -> session.getClassSessionId().equals(classSessionId))
                    .max(Comparator.comparing(LiveSummarySession::getStartedAt));
        }

        @Override
        public Optional<LiveSummarySession> findRunningByClassSessionId(UUID classSessionId) {
            return sessions.stream()
                    .filter(session -> session.getClassSessionId().equals(classSessionId))
                    .filter(session -> LiveSummaryStatus.RUNNING.name().equals(session.getStatus()))
                    .findFirst();
        }
    }

    private static class InMemoryLiveTranscriptSegmentRepository implements LiveTranscriptSegmentRepository {

        private final List<LiveTranscriptSegment> segments = new ArrayList<>();

        @Override
        public void save(LiveTranscriptSegment segment) {
            segments.add(segment);
        }

        @Override
        public int nextSequenceNo(UUID summarySessionId) {
            return segments.stream()
                    .filter(segment -> segment.getSummarySessionId().equals(summarySessionId))
                    .map(LiveTranscriptSegment::getSequenceNo)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
        }

        @Override
        public List<LiveTranscriptSegment> findRecentBySummarySessionId(UUID summarySessionId, int limit) {
            return segments.stream()
                    .filter(segment -> segment.getSummarySessionId().equals(summarySessionId))
                    .sorted(Comparator.comparing(LiveTranscriptSegment::getSequenceNo).reversed())
                    .limit(limit)
                    .sorted(Comparator.comparing(LiveTranscriptSegment::getSequenceNo))
                    .toList();
        }

        @Override
        public List<LiveTranscriptSegment> findAfterSequence(UUID summarySessionId, int sequenceNo) {
            return segments.stream()
                    .filter(segment -> segment.getSummarySessionId().equals(summarySessionId))
                    .filter(segment -> segment.getSequenceNo() > sequenceNo)
                    .sorted(Comparator.comparing(LiveTranscriptSegment::getSequenceNo))
                    .toList();
        }
    }

    private static class InMemoryLiveSummarySnapshotRepository implements LiveSummarySnapshotRepository {

        private final List<LiveSummarySnapshot> snapshots = new ArrayList<>();

        @Override
        public void save(LiveSummarySnapshot snapshot) {
            snapshot.setCreatedAt(Instant.now());
            snapshots.add(snapshot);
        }

        @Override
        public int nextSequenceNo(UUID summarySessionId) {
            return snapshots.stream()
                    .filter(snapshot -> snapshot.getSummarySessionId().equals(summarySessionId))
                    .map(LiveSummarySnapshot::getSequenceNo)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
        }

        @Override
        public Optional<LiveSummarySnapshot> findLatestBySummarySessionId(UUID summarySessionId) {
            return snapshots.stream()
                    .filter(snapshot -> snapshot.getSummarySessionId().equals(summarySessionId))
                    .max(Comparator.comparing(LiveSummarySnapshot::getSequenceNo));
        }
    }
}
