package com.codingjudge.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * The saturation notice must not spam the logs: during a burst the poller
 * ticks every second while the pool stays full for minutes.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionWorkerLogTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionService submissionService;
    @Mock private ThreadPoolTaskExecutor executor;

    private SubmissionWorker worker;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        worker = new SubmissionWorker(
                submissionRepository, submissionService, executor, true, 8);
        Logger logger = (Logger) LoggerFactory.getLogger(SubmissionWorker.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(SubmissionWorker.class);
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    @Test
    void saturationWarnsOncePerMinute() {
        when(submissionRepository.findIdsByStatusOrderBySubmittedAtAsc(
                eq(SubmissionStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RejectedExecutionException("full"))
                .when(executor).execute(any(Runnable.class));

        worker.poll();
        worker.poll();

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)).hasSize(1);
    }
}
