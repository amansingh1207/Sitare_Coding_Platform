package com.codingjudge.repository;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.SubmissionTestResult;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.entity.User;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.Role;
import com.codingjudge.model.enums.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SubmissionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private SubmissionTestResultRepository submissionTestResultRepository;

    private User user;
    private Problem problem;
    private TestCase sampleCase;
    private TestCase hiddenCase;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("solver@uni.edu");
        user.setUsername("solver");
        user.setPasswordHash("$2a$12$hashedpasswordplaceholder");
        user.setFullName("Solver Student");
        user.setRole(Role.STUDENT);
        userRepository.save(user);

        problem = new Problem();
        problem.setSlug("power-cut");
        problem.setTitle("Power Cut");
        problem.setStatement("Statement");
        problem.setInputFormat("Input format");
        problem.setOutputFormat("Output format");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");

        sampleCase = new TestCase();
        sampleCase.setInputData("2\n3");
        sampleCase.setExpectedOutput("6");
        sampleCase.setSample(true);
        sampleCase.setSortOrder(0);
        problem.addTestCase(sampleCase);

        hiddenCase = new TestCase();
        hiddenCase.setInputData("10");
        hiddenCase.setExpectedOutput("100");
        hiddenCase.setSample(false);
        hiddenCase.setSortOrder(1);
        problem.addTestCase(hiddenCase);

        problemRepository.save(problem);
    }

    private Submission newSubmission(Language language, SubmissionStatus status) {
        Submission submission = new Submission();
        submission.setUser(user);
        submission.setProblem(problem);
        submission.setLanguage(language);
        submission.setSourceCode("public class Main {}");
        submission.setStatus(status);
        return submission;
    }

    @Test
    void saveSubmissionDefaultsToPending() {
        Submission saved = submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.PENDING));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(saved.getSubmittedAt()).isNotNull();
        assertThat(saved.getJudgedAt()).isNull();
    }

    @Test
    void judgeResultPersistedWithPerTestResults() {
        Submission submission = submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.PENDING));

        SubmissionTestResult sampleResult = new SubmissionTestResult();
        sampleResult.setTestCase(sampleCase);
        sampleResult.setStatus(SubmissionStatus.ACCEPTED);
        sampleResult.setActualOutput("6");
        sampleResult.setRuntimeMs(150);
        sampleResult.setMemoryUsedKb(12800);
        submission.addTestResult(sampleResult);

        SubmissionTestResult hiddenResult = new SubmissionTestResult();
        hiddenResult.setTestCase(hiddenCase);
        hiddenResult.setStatus(SubmissionStatus.WRONG_ANSWER);
        hiddenResult.setActualOutput("99");
        hiddenResult.setRuntimeMs(140);
        hiddenResult.setMemoryUsedKb(12600);
        submission.addTestResult(hiddenResult);

        submission.setStatus(SubmissionStatus.WRONG_ANSWER);
        submission.setRuntimeMs(290);
        submission.setMemoryUsedKb(12800);
        submission.setJudgedAt(Instant.now());
        submissionRepository.save(submission);

        List<SubmissionTestResult> results =
                submissionTestResultRepository.findBySubmissionId(submission.getId());
        assertThat(results).hasSize(2);
    }

    @Test
    void findUserHistoryOrderedBySubmissionTime() {
        submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.WRONG_ANSWER));
        submissionRepository.save(newSubmission(Language.PYTHON, SubmissionStatus.ACCEPTED));

        List<Submission> history = submissionRepository.findByUserIdOrderBySubmittedAtDesc(user.getId());

        assertThat(history).hasSize(2);
    }

    @Test
    void filterByProblemStatusAndLanguage() {
        submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.ACCEPTED));
        submissionRepository.save(newSubmission(Language.CPP, SubmissionStatus.WRONG_ANSWER));

        assertThat(submissionRepository.findByUserIdAndProblemIdOrderBySubmittedAtDesc(
                user.getId(), problem.getId())).hasSize(2);
        assertThat(submissionRepository.findByUserIdAndStatus(user.getId(), SubmissionStatus.ACCEPTED))
                .hasSize(1);
        assertThat(submissionRepository.findByUserIdAndLanguage(user.getId(), Language.CPP))
                .hasSize(1);
        assertThat(submissionRepository.findByProblemId(problem.getId())).hasSize(2);
    }

    @Test
    void deletingSubmissionCascadesToTestResults() {
        Submission submission = submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.ACCEPTED));

        SubmissionTestResult result = new SubmissionTestResult();
        result.setTestCase(sampleCase);
        result.setStatus(SubmissionStatus.ACCEPTED);
        result.setActualOutput("6");
        submission.addTestResult(result);
        submissionRepository.save(submission);
        Long submissionId = submission.getId();

        submissionRepository.deleteById(submissionId);

        assertThat(submissionTestResultRepository.findBySubmissionId(submissionId)).isEmpty();
    }

    @Test
    void repeatedSubmissionsAllowed() {
        submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.WRONG_ANSWER));
        submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.WRONG_ANSWER));
        submissionRepository.save(newSubmission(Language.JAVA, SubmissionStatus.ACCEPTED));

        assertThat(submissionRepository.findByUserIdAndProblemIdOrderBySubmittedAtDesc(
                user.getId(), problem.getId())).hasSize(3);
    }
}
