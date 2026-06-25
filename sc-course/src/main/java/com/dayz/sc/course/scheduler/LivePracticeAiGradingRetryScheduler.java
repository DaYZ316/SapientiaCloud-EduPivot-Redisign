package com.dayz.sc.course.scheduler;

import com.dayz.sc.course.service.LivePracticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LivePracticeAiGradingRetryScheduler {

    private static final long RETRY_SCAN_DELAY_MS = 60_000L;

    private final LivePracticeService livePracticeService;

    @Scheduled(fixedDelay = RETRY_SCAN_DELAY_MS)
    public void resubmitPendingAiGradingRequests() {
        livePracticeService.resubmitPendingAiGradingRequests();
    }
}
