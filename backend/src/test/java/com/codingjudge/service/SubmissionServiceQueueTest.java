package com.codingjudge.service;

import com.codingjudge.judge.JudgeEngine;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.entity.User;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.SubmissionRepository;
import com.codingjudge.repository.SubmissionTestResultRepository;
import com.codingjudge.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers the pool-safe judging orchestration: claim, detached load, judge
 * with no connection held, then persist. Uses a real
 * {@link org.springframework.transaction.support.TransactionTemplate} over a
 * stubbed transaction manager, so the phase boundaries are exercised, not
 * mocked away.
 */
@ExtendWith(MockitoExtension.class)
class SubmissionServiceQueueTest {

    @Mock private SubmissionRepository submissionRepository;
    @Mock private SubmissionTestResultRepository testResultRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProblemRepository problemRepository;
    @Mock private JudgeEngine judgeEngine;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus transactionStatus;
    @Mock private RateLimiter rateLimiter;

    private SubmissionService submissionService;

    @BeforeEach
    void setUp() {
        // Needed only by judgeQueued (programmatic transactions); getForUser
        // tests never touch the manager, so keep this stub lenient.
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        submissionService = new SubmissionService(
                submissionRepository, testResultRepository, userRepository,
                problemRepository, judgeEngine, transactionManager, rateLimiter,
                256, true, 0, 0, 0);
    }

    private static Submission queuedSubmission() {
        Problem problem = new Problem();
        problem.addTestCase(new TestCase());
        Submission submission = new Submission();
        ReflectionTestUtils.setField(submission, "id", 7L);
        ReflectionTestUtils.setField(submission, "submittedAt", java.time.Instant.now());
        submission.setProblem(problem);
        submission.setLanguage(Language.JAVA);
        submission.setSourceCode("class Main{}");
        submission.setStatus(SubmissionStatus.PENDING);
        return submission;
    }

    @Test
    void lostClaimJudgesAndPersistsNothing() {
        when(submissionRepository.claimQueued(eq(7L), any(), any())).thenReturn(0);

        submissionService.judgeQueued(7L);

        verify(judgeEngine, never()).judge(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void happyPathJudgesDetachedAndPersistsVerdict() {
        Submission submission = queuedSubmission();
        when(submissionRepository.claimQueued(eq(7L), any(), any())).thenReturn(1);
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(judgeEngine.judge(any())).thenAnswer(invocation -> {
            Submission judged = invocation.getArgument(0);
            judged.setStatus(SubmissionStatus.ACCEPTED);
            return judged;
        });
        when(submissionRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0));

        submissionService.judgeQueued(7L);

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void judgeFailureMarksInternalError() {
        Submission submission = queuedSubmission();
        when(submissionRepository.claimQueued(eq(7L), any(), any())).thenReturn(1);
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(judgeEngine.judge(any())).thenThrow(new RuntimeException("provider down"));
        when(submissionRepository.save(any())).thenAnswer(
                invocation -> invocation.getArgument(0));

        submissionService.judgeQueued(7L);

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(SubmissionStatus.INTERNAL_ERROR);
        assertThat(saved.getValue().getJudgedAt()).isNotNull();
    }

    @Test
    void vanishedClaimMarksInternalError() {
        when(submissionRepository.claimQueued(eq(7L), any(), any())).thenReturn(1);
        when(submissionRepository.findById(7L)).thenReturn(Optional.empty());

        submissionService.judgeQueued(7L);

        // Load throws -> INTERNAL_ERROR marking reloads, finds nothing, saves nothing.
        verify(judgeEngine, never()).judge(any());
        verify(submissionRepository, never()).save(any());
    }

    @Test
    void pendingSubmissionReportsOneBasedQueuePosition() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 9L);
        Submission submission = queuedSubmission();
        submission.setUser(user);
        when(userRepository.findByEmail("u@uni.edu")).thenReturn(Optional.of(user));
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(testResultRepository.findBySubmissionId(7L)).thenReturn(List.of());
        when(submissionRepository.countByStatusAndSubmittedAtBefore(
                eq(SubmissionStatus.PENDING), any())).thenReturn(3L);

        var response = submissionService.getForUser("u@uni.edu", 7L);

        assertThat(response.getQueuePosition()).isEqualTo(4);
    }

    @Test
    void judgingSubmissionReportsNoQueuePosition() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 9L);
        Submission submission = queuedSubmission();
        submission.setUser(user);
        submission.setStatus(SubmissionStatus.JUDGING);
        when(userRepository.findByEmail("u@uni.edu")).thenReturn(Optional.of(user));
        when(submissionRepository.findById(7L)).thenReturn(Optional.of(submission));
        when(testResultRepository.findBySubmissionId(7L)).thenReturn(List.of());

        var response = submissionService.getForUser("u@uni.edu", 7L);

        assertThat(response.getQueuePosition()).isNull();
        verify(submissionRepository, never())
                .countByStatusAndSubmittedAtBefore(any(), any());
    }
}
