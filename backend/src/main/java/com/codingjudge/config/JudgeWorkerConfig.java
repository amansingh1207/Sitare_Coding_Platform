package com.codingjudge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Background judging infrastructure (Phase 2).
 *
 * <p>New file: the only scheduling/executor setup in the project. Nothing
 * existing was touched to enable it.
 */
@Configuration
@EnableScheduling
public class JudgeWorkerConfig {

    /**
     * Bounded pool for queued judging work. Core/max/queue are all capped so
     * a submission burst can delay verdicts but never exhaust threads or
     * memory. Rejected tasks are never silently dropped: the poller catches
     * the rejection and the row simply stays PENDING for the next tick.
     */
    @Bean(name = "submissionWorkerExecutor")
    public ThreadPoolTaskExecutor submissionWorkerExecutor(
            @Value("${judge.worker.threads.core:2}") int coreThreads,
            @Value("${judge.worker.threads.max:4}") int maxThreads,
            @Value("${judge.worker.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("judge-worker-");
        executor.setCorePoolSize(coreThreads);
        executor.setMaxPoolSize(maxThreads);
        executor.setQueueCapacity(queueCapacity);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // Let in-flight verdicts finish on graceful shutdown (Render SIGTERM).
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
