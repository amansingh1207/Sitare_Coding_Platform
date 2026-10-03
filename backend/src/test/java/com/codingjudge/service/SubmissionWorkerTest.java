package com.codingjudge.service;

import com.codingjudge.model.dto.request.SubmitRequest;
import com.codingjudge.model.dto.response.SubmissionRefResponse;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.entity.User;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.Role;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.ProblemRepository;
import com.codingjudge.repository.SubmissionRepository;
import com.codingjudge.repository.SubmissionTestResultRepository;
import com.codingjudge.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Async-path tests for the DB-backed judging queue (Phase 2).
 *
 * <p>Runs with the worker ENABLED (unlike the default test profile) and the
 * scheduled poll silenced, so each test drives {@link SubmissionWorker#poll()}
 * deterministically. Judging itself goes through the {@code TestJudgeConfig}
 * stub: no Docker daemon needed. No @Transactional here: worker threads run
 * in their own transactions and must see committed fixtures.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "judge.worker.enabled=true",
        "judge.worker.poll-ms=3600000",
        "judge.worker.max-claim-per-tick=20"
})
class SubmissionWorkerTest {

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private SubmissionWorker submissionWorker;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionTestResultRepository testResultRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private Problem problem;

    @BeforeEach
    void setUp() {
        testResultRepository.deleteAll();
        submissionRepository.deleteAll();
        problemRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("worker@uni.edu");
        user.setUsername("worker");
        user.setPasswordHash("hash");
        user.setFullName("Worker Test");
        user.setRole(Role.STUDENT);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        problem = new Problem();
        problem.setSlug("sum-two");
        problem.setTitle("Sum Two");
        problem.setStatement("Add them.");
        problem.setInputFormat("Two ints.");
        problem.setOutputFormat("Their sum.");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");
        TestCase sample = new TestCase();
        sample.setInputData("3 4");
        sample.setExpectedOutput("7");
        sample.setSample(true);
        sample.setSortOrder(0);
        TestCase hidden = new TestCase();
        hidden.setInputData("10 20");
        hidden.setExpectedOutput("30");
        hidden.setSample(false);
        hidden.setSortOrder(1);
        problem.addTestCase(sample);
        problem.addTestCase(hidden);
        problem = problemRepository.save(problem);
    }

    @AfterEach
    void tearDown() {
        testResultRepository.deleteAll();
        submissionRepository.deleteAll();
        problemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void submitReturnsPendingAndWorkerJudgesToTerminal() {
        SubmissionRefResponse ref = submit("public class Main {}");

        assertThat(ref.getStatus()).isEqualTo(SubmissionStatus.PENDING.name());

        submissionWorker.poll();
        Submission judged = awaitTerminal(ref.getId());

        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(testResultRepository.findBySubmissionId(judged.getId())).hasSize(2);
    }

    @Test
    void burstOfSubmissionsAllReachTerminal() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(submit("public class Main {}").getId());
        }

        submissionWorker.poll();

        for (Long id : ids) {
            assertThat(awaitTerminal(id).getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        }
        assertThat(submissionRepository.countByStatus(SubmissionStatus.PENDING)).isZero();
        assertThat(submissionRepository.countByStatus(SubmissionStatus.JUDGING)).isZero();
    }

    @Test
    void burstOfFiftySubmissionsDrainsCompletely() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ids.add(submit("public class Main {}").getId());
        }
        assertThat(submissionRepository.countByStatus(SubmissionStatus.PENDING)).isEqualTo(50);

        long started = System.currentTimeMillis();
        submissionWorker.poll();
        // One poll claims max-claim-per-tick; keep polling until drained.
        while (submissionRepository.countByStatus(SubmissionStatus.PENDING) > 0
                || submissionRepository.countByStatus(SubmissionStatus.JUDGING) > 0) {
            submissionWorker.poll();
            if (System.currentTimeMillis() - started > 60_000) {
                fail("Burst did not drain within 60 s");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while draining burst");
            }
        }
        long drainMs = System.currentTimeMillis() - started;
        System.out.println("[SubmissionWorkerTest] 50-burst drained in " + drainMs + " ms");

        for (Long id : ids) {
            assertThat(awaitTerminal(id).getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        }
        assertThat(testResultRepository.count()).isEqualTo(50L * 2L);
    }

    @Test
    void concurrentPollsNeverDuplicateResults() throws Exception {
        Long id = submit("public class Main {}").getId();

        ExecutorService racing = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            racing.submit(() -> {
                try {
                    start.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                submissionWorker.poll();
            });
        }
        start.countDown();
        racing.shutdown();
        assertThat(racing.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        Submission judged = awaitTerminal(id);
        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        // Exactly one result row per test case: the atomic claim allowed a
        // single winner, so nothing was judged twice.
        assertThat(testResultRepository.findBySubmissionId(id)).hasSize(2);
    }

    @Test
    void startupRecoveryRequeuesStrandedJudging() {
        Submission stranded = new Submission();
        stranded.setUser(user);
        stranded.setProblem(problem);
        stranded.setLanguage(Language.JAVA);
        stranded.setSourceCode("public class Main {}");
        stranded.setStatus(SubmissionStatus.JUDGING);
        stranded = submissionRepository.save(stranded);

        submissionWorker.recoverOnStartup();
        assertThat(submissionRepository.findById(stranded.getId()).orElseThrow().getStatus())
                .isEqualTo(SubmissionStatus.PENDING);

        submissionWorker.poll();
        assertThat(awaitTerminal(stranded.getId()).getStatus())
                .isEqualTo(SubmissionStatus.ACCEPTED);
    }

    private SubmissionRefResponse submit(String sourceCode) {
        SubmitRequest request = new SubmitRequest();
        request.setProblemId(problem.getId());
        request.setLanguage("JAVA");
        request.setSourceCode(sourceCode);
        return submissionService.submit(user.getEmail(), request);
    }

    private Submission awaitTerminal(Long id) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (true) {
            Submission current = submissionRepository.findById(id).orElseThrow();
            if (current.getStatus() != SubmissionStatus.PENDING
                    && current.getStatus() != SubmissionStatus.JUDGING) {
                return current;
            }
            if (System.currentTimeMillis() > deadline) {
                fail("Submission " + id + " never reached a terminal state");
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for submission " + id);
            }
        }
    }
}
