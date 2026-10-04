package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.SubmissionTestResult;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionTestResultRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Live DOMjudge integration (Phase 9 design proof): the real provider drives
 * the real engine against a real DOMjudge, no Spring context, no Docker judge.
 *
 * <p>Runs only when explicitly configured (never secrets in repo):
 * {@code DOMJUDGE_TEST_URL}, {@code DOMJUDGE_TEST_CONTEST},
 * {@code DOMJUDGE_TEST_USER}, {@code DOMJUDGE_TEST_PASSWORD} (a team+admin
 * service account). Otherwise every test skips and the suite stays green.
 */
class DomjudgeLiveEngineTest {

    private static JudgeEngine engine;
    private static SubmissionTestResultRepository testResults;

    @BeforeAll
    static void connect() {
        String baseUrl = System.getenv("DOMJUDGE_TEST_URL");
        String contest = System.getenv("DOMJUDGE_TEST_CONTEST");
        String user = System.getenv("DOMJUDGE_TEST_USER");
        String password = System.getenv("DOMJUDGE_TEST_PASSWORD");
        assumeTrue(baseUrl != null && !baseUrl.isBlank()
                        && contest != null && !contest.isBlank()
                        && user != null && !user.isBlank() && password != null,
                "DOMjudge live test env not set - skipping");
        DomjudgeClient client = new DomjudgeClient(baseUrl, user, password);
        try {
            client.listLanguages();
        } catch (DomjudgeClient.DomjudgeException e) {
            assumeTrue(false, "DOMjudge not reachable at " + baseUrl + " - skipping");
            return;
        }
        DomjudgeExecutionService provider = new DomjudgeExecutionService(client,
                new DomjudgeProblemMirror(client, contest), contest, 2000, 300000);
        testResults = mock(SubmissionTestResultRepository.class);
        engine = new JudgeEngine(provider, new OutputComparator(), testResults,
                new JavaExecutor(), new CppExecutor(), new PythonExecutor());
        System.out.println("[DomjudgeLive] contest=" + contest + " baseUrl=" + baseUrl);
    }

    private Problem problem() {
        Problem problem = new Problem();
        problem.setId(999L);
        problem.setSlug("sum-live");
        problem.setTitle("Sum Live");
        problem.setStatement("Add.");
        problem.setInputFormat("Ints.");
        problem.setOutputFormat("Sum.");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitMb(256);
        addCase(problem, 11L, "3 4", "7", true, 0);
        addCase(problem, 12L, "10 20", "30", false, 1);
        addCase(problem, 13L, "", "0", false, 2);
        return problem;
    }

    private void addCase(Problem problem, long id, String input, String expected,
                         boolean sample, int order) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setInputData(input);
        testCase.setExpectedOutput(expected);
        testCase.setSample(sample);
        testCase.setSortOrder(order);
        problem.addTestCase(testCase);
    }

    private Submission judge(Language language, String sourceCode) {
        Submission submission = new Submission();
        submission.setLanguage(language);
        submission.setSourceCode(sourceCode);
        submission.setProblem(problem());
        return engine.judge(submission);
    }

    private static final String ROBUST_SUM_PY =
            "import sys\ndata = sys.stdin.read().strip().split()\n"
                    + "print(0 if not data else sum(map(int, data)))\n";

    @Test
    void pythonAcceptedAcrossAllCasesIncludingEmpty() {
        Submission judged = judge(Language.PYTHON, ROBUST_SUM_PY);

        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void javaAccepted() {
        String source = "import java.util.*;\n"
                + "public class Main {\n"
                + "  public static void main(String[] args) {\n"
                + "    Scanner sc = new Scanner(System.in);\n"
                + "    long sum = 0;\n"
                + "    while (sc.hasNextLong()) sum += sc.nextLong();\n"
                + "    System.out.println(sum);\n"
                + "  }\n"
                + "}\n";

        assertThat(judge(Language.JAVA, source).getStatus())
                .isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void cppAccepted() {
        String source = "#include <bits/stdc++.h>\n"
                + "using namespace std;\n"
                + "int main() {\n"
                + "  ios::sync_with_stdio(false);\n"
                + "  long long x, sum = 0;\n"
                + "  bool any = false;\n"
                + "  while (cin >> x) { sum += x; any = true; }\n"
                + "  cout << sum;\n"
                + "  return 0;\n"
                + "}\n";

        assertThat(judge(Language.CPP, source).getStatus())
                .isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void perCaseVerdictsAlignToRequestingTestCases() {
        // Always prints 0: case 11 ("7") and 12 ("30") must be WA while the
        // empty-input case 13 ("0") is AC. Exact id+status pairs prove runs
        // map to the right test cases (the ordinal-alignment guard).
        clearInvocations(testResults);
        Submission judged = judge(Language.PYTHON, "print(0)");

        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        ArgumentCaptor<SubmissionTestResult> captor =
                ArgumentCaptor.forClass(SubmissionTestResult.class);
        verify(testResults, times(3)).save(captor.capture());
        Map<Long, SubmissionStatus> byCase = new HashMap<>();
        for (SubmissionTestResult result : captor.getAllValues()) {
            byCase.put(result.getTestCase().getId(), result.getStatus());
        }
        assertThat(byCase).containsEntry(11L, SubmissionStatus.WRONG_ANSWER)
                .containsEntry(12L, SubmissionStatus.WRONG_ANSWER)
                .containsEntry(13L, SubmissionStatus.ACCEPTED);
    }

    @Test
    void compilationErrorDetected() {
        String source = "public class Main {\n"
                + "  public static void main(String[] args) {\n"
                + "    System.out.println(\"oops\")\n"
                + "  }\n"
                + "}\n";

        assertThat(judge(Language.JAVA, source).getStatus())
                .isEqualTo(SubmissionStatus.COMPILATION_ERROR);
    }

    @Test
    void runtimeErrorDetected() {
        assertThat(judge(Language.PYTHON, "print(1 // 0)").getStatus())
                .isEqualTo(SubmissionStatus.RUNTIME_ERROR);
    }

    @Test
    void timeLimitExceeded() {
        assertThat(judge(Language.PYTHON, "while True:\n  pass").getStatus())
                .isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
    }
}
