package com.codingjudge.service;

import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * Background judging worker (Phase 2).
 *
 * <p>The {@code submissions} table is the durable queue: {@code PENDING} rows
 * are queued work, {@code JUDGING} rows are claimed work. No in-memory queue,
 * no broker, so a Render restart loses nothing.
 *
 * <ul>
 *   <li>Claim is one atomic {@code UPDATE ... WHERE status=PENDING} per row:
 *       exactly one worker wins, duplicates are impossible in practice.</li>
 *   <li>Every claim is judged inside its own transaction and always lands in
 *       a terminal state (catch-all maps surprises to {@code INTERNAL_ERROR}).
 *       Only a JVM death can strand a row in {@code JUDGING}, and startup
 *       recovery requeues those.</li>
 *   <li>Concurrency is bounded by the {@code submissionWorkerExecutor} pool;
 *       the poller never dispatches more than the pool can take.</li>
 * </ul>
 */
@Service
public class SubmissionWorker {

    private static final Logger LOG = LoggerFactory.getLogger(SubmissionWorker.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionService submissionService;
    private final ThreadPoolTaskExecutor executor;
    private final boolean enabled;
    private final int maxClaimPerTick;
    /**
     * Saturation warnings are throttled: a burst pins the pool for minutes
     * and the poller ticks every second, so an unthrottled WARN buried the
     * logs (and real issues) in thousands of identical lines.
     */
    private volatile long lastSaturationWarnMs = 0;
    static final long SATURATION_WARN_INTERVAL_MS = 60_000;

    public SubmissionWorker(SubmissionRepository submissionRepository,
                           SubmissionService submissionService,
                           @Qualifier("submissionWorkerExecutor") ThreadPoolTaskExecutor executor,
                           @Value("${judge.worker.enabled:true}") boolean enabled,
                           @Value("${judge.worker.max-claim-per-tick:8}") int maxClaimPerTick) {
        this.submissionRepository = submissionRepository;
        this.submissionService = submissionService;
        this.executor = executor;
        this.enabled = enabled;
        this.maxClaimPerTick = maxClaimPerTick;
    }

    /** Crash recovery: rows stranded in JUDGING by a dead instance go home. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        if (!enabled) {
            return;
        }
        int requeued = submissionRepository.resetJudgingToPending(
                SubmissionStatus.JUDGING, SubmissionStatus.PENDING);
        if (requeued > 0) {
            LOG.warn("Requeued {} submissions stranded in JUDGING by a previous run", requeued);
        }
    }

    /** Claim queued rows and hand them to the bounded pool. Runs on a timer. */
    @Scheduled(fixedDelayString = "${judge.worker.poll-ms:1000}")
    public void poll() {
        if (!enabled) {
            return;
        }
        List<Long> ids;
        try {
            ids = submissionRepository.findIdsByStatusOrderBySubmittedAtAsc(
                    SubmissionStatus.PENDING, PageRequest.of(0, maxClaimPerTick));
        } catch (RuntimeException e) {
            // The DB hiccuped; next tick retries. Never kill the scheduler.
            LOG.error("Submission poll failed, retrying next tick: {}", e.getMessage());
            return;
        }
        for (Long id : ids) {
            try {
                executor.execute(() -> submissionService.judgeQueued(id));
            } catch (RejectedExecutionException e) {
                // Pool saturated: rows stay PENDING, next tick retries.
                long now = System.currentTimeMillis();
                if (now - lastSaturationWarnMs >= SATURATION_WARN_INTERVAL_MS) {
                    lastSaturationWarnMs = now;
                    LOG.warn("Worker pool saturated, deferring {} queued submissions "
                            + "(further notices throttled 60s)", ids.size());
                } else {
                    LOG.debug("Worker pool saturated, deferring {} queued submissions",
                            ids.size());
                }
                return;
            }
        }
    }
}
