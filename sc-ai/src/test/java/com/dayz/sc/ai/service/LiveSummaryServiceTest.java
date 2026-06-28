package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.LiveSummarySession;
import com.dayz.sc.ai.model.entity.LiveSummarySnapshot;
import com.dayz.sc.ai.model.entity.LiveTranscriptSegment;
import com.dayz.sc.ai.model.enums.LiveSummaryStatus;
import com.dayz.sc.ai.model.vo.LiveSummarySessionVO;
import com.dayz.sc.ai.model.vo.LiveSummarySnapshotVO;
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
import java.util.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    LiveSummaryServiceTest() {
        sessionRepository.snapshotRepository = snapshotRepository;
    }

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
    void startShouldRejectWhenClassSessionHistoryRecordLimitReached() {
        UUID classSessionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        givenAiConfigured();
        for (int index = 0; index < 5; index++) {
            LiveSummarySession existingSession = session(classSessionId, courseId, teacherId);
            existingSession.setStatus(LiveSummaryStatus.STOPPED.name());
            sessionRepository.save(existingSession);
        }

        assertThatThrownBy(() -> service.start(classSessionId, teacherId, 2))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void startShouldNotCountMultipleSnapshotsAsHistoryRecords() {
        UUID classSessionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        givenAiConfigured();
        LiveSummarySession existingSession = session(classSessionId, courseId, teacherId);
        existingSession.setStatus(LiveSummaryStatus.STOPPED.name());
        sessionRepository.save(existingSession);
        for (int sequenceNo = 1; sequenceNo <= 5; sequenceNo++) {
            snapshotRepository.save(snapshot(existingSession.getId(), classSessionId, sequenceNo));
        }

        LiveSummarySessionVO result = service.start(classSessionId, teacherId, 2);

        assertThat(result.status()).isEqualTo(LiveSummaryStatus.RUNNING.name());
    }

    @Test
    void startShouldIgnoreOtherClassSessionsInSameCourseForHistoryRecordLimit() {
        UUID classSessionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        givenAiConfigured();
        for (int index = 0; index < 5; index++) {
            LiveSummarySession existingSession = session(UUID.randomUUID(), courseId, teacherId);
            existingSession.setStatus(LiveSummaryStatus.STOPPED.name());
            sessionRepository.save(existingSession);
        }

        LiveSummarySessionVO result = service.start(classSessionId, teacherId, 2);

        assertThat(result.status()).isEqualTo(LiveSummaryStatus.RUNNING.name());
    }

    @Test
    void resumeShouldRestartStoppedSessionWithoutCreatingNewSession() {
        UUID classSessionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        givenAiConfigured();
        LiveSummarySession session = session(classSessionId, courseId, teacherId);
        session.setStatus(LiveSummaryStatus.STOPPED.name());
        session.setStoppedAt(Instant.now());
        sessionRepository.save(session);
        LiveSummarySession newerSession = session(classSessionId, courseId, teacherId);
        newerSession.setStatus(LiveSummaryStatus.STOPPED.name());
        newerSession.setStartedAt(session.getStartedAt().plusSeconds(60));
        sessionRepository.save(newerSession);
        for (int sequenceNo = 1; sequenceNo <= 5; sequenceNo++) {
            snapshotRepository.save(snapshot(session.getId(), classSessionId, sequenceNo));
        }

        LiveSummarySessionVO result = service.resume(classSessionId, session.getId(), teacherId, 2);

        assertThat(result.id()).isEqualTo(session.getId());
        assertThat(result.status()).isEqualTo(LiveSummaryStatus.RUNNING.name());
        assertThat(result.stoppedAt()).isNull();
        assertThat(sessionRepository.sessions).hasSize(2);
        assertThat(service.get(classSessionId, teacherId, 2).id()).isEqualTo(session.getId());
    }

    @Test
    void resumeShouldRejectSessionFromAnotherClassSession() {
        UUID classSessionId = UUID.randomUUID();
        UUID otherClassSessionId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        givenAiConfigured();
        LiveSummarySession otherSession = session(otherClassSessionId, courseId, teacherId);
        otherSession.setStatus(LiveSummaryStatus.STOPPED.name());
        sessionRepository.save(otherSession);

        assertThatThrownBy(() -> service.resume(classSessionId, otherSession.getId(), teacherId, 2))
                .isInstanceOf(BusinessException.class);
        assertThat(otherSession.getStatus()).isEqualTo(LiveSummaryStatus.STOPPED.name());
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

    @Test
    void clearShouldRemoveSummaryDataAndReturnEmptySession() {
        UUID classSessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        givenAccess(classSessionId, UUID.randomUUID(), UUID.randomUUID(), true, true);
        givenAiConfigured();
        LiveSummarySessionVO started = service.start(classSessionId, userId, 2);
        UUID summarySessionId = started.id();
        transcriptRepository.save(segment(summarySessionId, classSessionId, userId, 1, "class topic"));
        LiveSummarySnapshot snapshot = new LiveSummarySnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setSummarySessionId(summarySessionId);
        snapshot.setClassSessionId(classSessionId);
        snapshot.setSequenceNo(1);
        snapshot.setTranscriptUntilSequenceNo(1);
        snapshot.setOverview("class overview");
        snapshot.setPayload(Map.of("overview", "class overview"));
        snapshotRepository.save(snapshot);

        LiveSummarySessionVO result = service.clear(classSessionId, userId, 2);

        assertThat(result.id()).isNull();
        assertThat(result.status()).isEqualTo("NOT_STARTED");
        assertThat(sessionRepository.sessions).isEmpty();
        assertThat(transcriptRepository.segments).isEmpty();
        assertThat(snapshotRepository.snapshots).isEmpty();
    }

    @Test
    void deleteSnapshotShouldAllowOpeningTeacher() {
        UUID classSessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        LiveSummarySession session = session(classSessionId, courseId, teacherId);
        sessionRepository.save(session);
        LiveSummarySnapshot oldSnapshot = snapshot(session.getId(), classSessionId, 1);
        LiveSummarySnapshot latestSnapshot = snapshot(session.getId(), classSessionId, 2);
        snapshotRepository.save(oldSnapshot);
        snapshotRepository.save(latestSnapshot);

        List<LiveSummarySnapshotVO> result = service.deleteSnapshot(classSessionId, latestSnapshot.getId(), teacherId, 2);

        assertThat(latestSnapshot.getDeleted()).isEqualTo(1);
        assertThat(result).extracting(LiveSummarySnapshotVO::id).containsExactly(oldSnapshot.getId());
    }

    @Test
    void listSnapshotsShouldIncludeSnapshotsFromPreviousSummarySessions() {
        UUID classSessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        LiveSummarySession firstSession = session(classSessionId, courseId, teacherId);
        LiveSummarySession latestSession = session(classSessionId, courseId, teacherId);
        latestSession.setStartedAt(firstSession.getStartedAt().plusSeconds(60));
        sessionRepository.save(firstSession);
        sessionRepository.save(latestSession);
        LiveSummarySnapshot oldSnapshot = snapshot(firstSession.getId(), classSessionId, 1);
        LiveSummarySnapshot latestSnapshot = snapshot(latestSession.getId(), classSessionId, 1);
        oldSnapshot.setCreatedAt(Instant.now());
        latestSnapshot.setCreatedAt(oldSnapshot.getCreatedAt().plusSeconds(60));
        snapshotRepository.save(oldSnapshot);
        snapshotRepository.save(latestSnapshot);

        List<LiveSummarySnapshotVO> result = service.listSnapshots(classSessionId, teacherId, 2);

        assertThat(result).extracting(LiveSummarySnapshotVO::id)
                .containsExactly(latestSnapshot.getId(), oldSnapshot.getId());
    }

    @Test
    void listSnapshotsShouldIncludeSnapshotsFromSameCourseClassSessions() {
        UUID classSessionId = UUID.randomUUID();
        UUID previousClassSessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        LiveSummarySession previousSession = session(previousClassSessionId, courseId, teacherId);
        LiveSummarySession currentSession = session(classSessionId, courseId, teacherId);
        currentSession.setStartedAt(previousSession.getStartedAt().plusSeconds(60));
        sessionRepository.save(previousSession);
        sessionRepository.save(currentSession);
        LiveSummarySnapshot oldSnapshot = snapshot(previousSession.getId(), previousClassSessionId, 1);
        LiveSummarySnapshot latestSnapshot = snapshot(currentSession.getId(), classSessionId, 1);
        oldSnapshot.setCreatedAt(Instant.now());
        latestSnapshot.setCreatedAt(oldSnapshot.getCreatedAt().plusSeconds(60));
        snapshotRepository.save(oldSnapshot);
        snapshotRepository.save(latestSnapshot);

        List<LiveSummarySnapshotVO> result = service.listSnapshots(classSessionId, teacherId, 2);

        assertThat(result).extracting(LiveSummarySnapshotVO::id)
                .containsExactly(latestSnapshot.getId(), oldSnapshot.getId());
    }

    @Test
    void deleteSnapshotShouldRejectCourseManagerWhoIsNotOpeningTeacher() {
        UUID classSessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        LiveSummarySession session = session(classSessionId, courseId, teacherId);
        sessionRepository.save(session);
        LiveSummarySnapshot snapshot = snapshot(session.getId(), classSessionId, 1);
        snapshotRepository.save(snapshot);

        assertThatThrownBy(() -> service.deleteSnapshot(classSessionId, snapshot.getId(), managerId, 2))
                .isInstanceOf(BusinessException.class);
        assertThat(snapshotRepository.snapshots).containsExactly(snapshot);
    }

    @Test
    void nextSnapshotSequenceShouldNotReuseDeletedSnapshotSequence() {
        UUID classSessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        LiveSummarySession session = session(classSessionId, courseId, teacherId);
        sessionRepository.save(session);
        LiveSummarySnapshot firstSnapshot = snapshot(session.getId(), classSessionId, 1);
        snapshotRepository.save(firstSnapshot);

        service.deleteSnapshot(classSessionId, firstSnapshot.getId(), teacherId, 2);

        assertThat(snapshotRepository.nextSequenceNo(session.getId())).isEqualTo(2);
    }

    @Test
    void deleteHistoryRecordShouldReleaseClassSessionHistoryCapacity() {
        UUID classSessionId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        givenAccess(classSessionId, courseId, teacherId, true, true);
        givenAiConfigured();
        LiveSummarySession deletedSession = null;
        for (int index = 0; index < 5; index++) {
            LiveSummarySession session = session(classSessionId, courseId, teacherId);
            session.setStatus(LiveSummaryStatus.STOPPED.name());
            sessionRepository.save(session);
            snapshotRepository.save(snapshot(session.getId(), session.getClassSessionId(), 1));
            deletedSession = session;
        }

        service.deleteHistoryRecord(classSessionId, deletedSession.getId(), teacherId, 2);
        LiveSummarySessionVO started = service.start(classSessionId, teacherId, 2);

        assertThat(started.status()).isEqualTo(LiveSummaryStatus.RUNNING.name());
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

    private LiveSummarySession session(UUID classSessionId, UUID courseId, UUID teacherId) {
        LiveSummarySession session = new LiveSummarySession();
        session.setId(UUID.randomUUID());
        session.setClassSessionId(classSessionId);
        session.setCourseId(courseId);
        session.setTeacherId(teacherId);
        session.setStatus(LiveSummaryStatus.RUNNING.name());
        session.setStartedAt(Instant.now());
        return session;
    }

    private LiveSummarySnapshot snapshot(UUID summarySessionId, UUID classSessionId, int sequenceNo) {
        LiveSummarySnapshot snapshot = new LiveSummarySnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setSummarySessionId(summarySessionId);
        snapshot.setClassSessionId(classSessionId);
        snapshot.setSequenceNo(sequenceNo);
        snapshot.setTranscriptUntilSequenceNo(sequenceNo);
        snapshot.setOverview("snapshot " + sequenceNo);
        snapshot.setPayload(Map.of("overview", "snapshot " + sequenceNo));
        return snapshot;
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
        private InMemoryLiveSummarySnapshotRepository snapshotRepository;

        @Override
        public void save(LiveSummarySession session) {
            sessions.add(session);
            if (snapshotRepository != null) {
                snapshotRepository.registerSession(session.getId(), session.getCourseId());
            }
        }

        @Override
        public void update(LiveSummarySession session) {
            // Entities are kept by reference in this in-memory repository.
        }

        @Override
        public void resume(LiveSummarySession session) {
            update(session);
        }

        @Override
        public Optional<LiveSummarySession> findById(UUID id) {
            return sessions.stream()
                    .filter(session -> session.getId().equals(id))
                    .filter(session -> !Objects.equals(session.getDeleted(), 1))
                    .findFirst();
        }

        @Override
        public Optional<LiveSummarySession> findLatestByClassSessionId(UUID classSessionId) {
            return sessions.stream()
                    .filter(session -> session.getClassSessionId().equals(classSessionId))
                    .filter(session -> !Objects.equals(session.getDeleted(), 1))
                    .max(Comparator.comparing(LiveSummarySession::getStartedAt));
        }

        @Override
        public Optional<LiveSummarySession> findRunningByClassSessionId(UUID classSessionId) {
            return sessions.stream()
                    .filter(session -> session.getClassSessionId().equals(classSessionId))
                    .filter(session -> !Objects.equals(session.getDeleted(), 1))
                    .filter(session -> LiveSummaryStatus.RUNNING.name().equals(session.getStatus()))
                    .findFirst();
        }

        @Override
        public List<LiveSummarySession> findWithSnapshotsByCourseIds(List<UUID> courseIds, int page, int size) {
            Set<UUID> courseIdSet = new HashSet<>(courseIds);
            return sessions.stream()
                    .filter(session -> !Objects.equals(session.getDeleted(), 1))
                    .filter(session -> courseIdSet.isEmpty() || courseIdSet.contains(session.getCourseId()))
                    .filter(session -> snapshotRepository != null
                            && snapshotRepository.findLatestBySummarySessionId(session.getId()).isPresent())
                    .sorted(Comparator.comparing((LiveSummarySession session) ->
                            snapshotRepository.findLatestBySummarySessionId(session.getId())
                                    .map(LiveSummarySnapshot::getCreatedAt)
                                    .orElse(Instant.EPOCH)).reversed())
                    .skip((long) (Math.max(1, page) - 1) * Math.max(1, size))
                    .limit(Math.max(1, size))
                    .toList();
        }

        @Override
        public long countWithSnapshotsByCourseIds(List<UUID> courseIds) {
            Set<UUID> courseIdSet = new HashSet<>(courseIds);
            return sessions.stream()
                    .filter(session -> !Objects.equals(session.getDeleted(), 1))
                    .filter(session -> courseIdSet.isEmpty() || courseIdSet.contains(session.getCourseId()))
                    .filter(session -> snapshotRepository != null
                            && snapshotRepository.findLatestBySummarySessionId(session.getId()).isPresent())
                    .count();
        }

        @Override
        public int countByClassSessionId(UUID classSessionId) {
            return Math.toIntExact(sessions.stream()
                    .filter(session -> session.getClassSessionId().equals(classSessionId))
                    .filter(session -> !Objects.equals(session.getDeleted(), 1))
                    .count());
        }

        @Override
        public void deleteById(UUID id) {
            sessions.stream()
                    .filter(session -> session.getId().equals(id))
                    .forEach(session -> session.setDeleted(1));
        }

        @Override
        public void deleteByClassSessionId(UUID classSessionId) {
            sessions.removeIf(session -> session.getClassSessionId().equals(classSessionId));
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

        @Override
        public void deleteBySummarySessionId(UUID summarySessionId) {
            segments.removeIf(segment -> segment.getSummarySessionId().equals(summarySessionId));
        }

        @Override
        public void deleteByClassSessionId(UUID classSessionId) {
            segments.removeIf(segment -> segment.getClassSessionId().equals(classSessionId));
        }
    }

    private static class InMemoryLiveSummarySnapshotRepository implements LiveSummarySnapshotRepository {

        private final List<LiveSummarySnapshot> snapshots = new ArrayList<>();
        private final Map<UUID, UUID> courseIdBySummarySessionId = new HashMap<>();

        void registerSession(UUID summarySessionId, UUID courseId) {
            courseIdBySummarySessionId.put(summarySessionId, courseId);
        }

        @Override
        public void save(LiveSummarySnapshot snapshot) {
            if (snapshot.getCreatedAt() == null) {
                snapshot.setCreatedAt(Instant.now());
            }
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
                    .filter(snapshot -> !Objects.equals(snapshot.getDeleted(), 1))
                    .max(Comparator.comparing(LiveSummarySnapshot::getSequenceNo));
        }

        @Override
        public Optional<LiveSummarySnapshot> findById(UUID id) {
            return snapshots.stream()
                    .filter(snapshot -> snapshot.getId().equals(id))
                    .filter(snapshot -> !Objects.equals(snapshot.getDeleted(), 1))
                    .findFirst();
        }

        @Override
        public List<LiveSummarySnapshot> findRecentBySummarySessionId(UUID summarySessionId, int limit) {
            return snapshots.stream()
                    .filter(snapshot -> snapshot.getSummarySessionId().equals(summarySessionId))
                    .filter(snapshot -> !Objects.equals(snapshot.getDeleted(), 1))
                    .sorted(Comparator.comparing(LiveSummarySnapshot::getSequenceNo).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<LiveSummarySnapshot> findRecentByClassSessionId(UUID classSessionId, int limit) {
            return snapshots.stream()
                    .filter(snapshot -> snapshot.getClassSessionId().equals(classSessionId))
                    .filter(snapshot -> !Objects.equals(snapshot.getDeleted(), 1))
                    .sorted(Comparator.comparing(LiveSummarySnapshot::getCreatedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<LiveSummarySnapshot> findRecentByCourseId(UUID courseId, int limit) {
            return snapshots.stream()
                    .filter(snapshot -> courseId.equals(courseIdBySummarySessionId.get(snapshot.getSummarySessionId())))
                    .filter(snapshot -> !Objects.equals(snapshot.getDeleted(), 1))
                    .sorted(Comparator.comparing(LiveSummarySnapshot::getCreatedAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public void deleteById(UUID id) {
            snapshots.stream()
                    .filter(snapshot -> snapshot.getId().equals(id))
                    .forEach(snapshot -> snapshot.setDeleted(1));
        }

        @Override
        public void deleteBySummarySessionId(UUID summarySessionId) {
            snapshots.removeIf(snapshot -> snapshot.getSummarySessionId().equals(summarySessionId));
        }

        @Override
        public void deleteByClassSessionId(UUID classSessionId) {
            snapshots.removeIf(snapshot -> snapshot.getClassSessionId().equals(classSessionId));
        }
    }
}
