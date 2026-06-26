package com.dayz.sc.course.scheduler;

import com.dayz.sc.course.service.ClassSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Closes class live streams after the class session end time.
 *
 * @author DaYZ
 * @since 2026-06-21
 */
@Component
@RequiredArgsConstructor
public class ClassLiveEndScheduler {

    private static final long LIVE_END_SCAN_DELAY_MS = 60_000L;
    private static final long LIVE_HEARTBEAT_SCAN_DELAY_MS = 15_000L;

    private final ClassSessionService classSessionService;

    @Scheduled(fixedDelay = LIVE_END_SCAN_DELAY_MS)
    public void endExpiredLiveSessions() {
        classSessionService.endExpiredLiveSessions();
    }

    @Scheduled(fixedDelay = LIVE_HEARTBEAT_SCAN_DELAY_MS)
    public void pauseDisconnectedLiveSessions() {
        classSessionService.pauseDisconnectedLiveSessions();
    }
}
