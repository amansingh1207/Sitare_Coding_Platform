package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Difficulty;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionTestResultRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Live provider integration (Phase 2): the real {@link Judge0ExecutionService}
 * drives the real {@link JudgeEngine} against a real Judge0 instance, with no
 * Spring context and no Docker involved.
 *
 * <p>Needs Judge0 reachable ({@code docker compose -f docker-compose.judge0.yml
 * up -d}, or a hosted URL via env). Skips cleanly otherwise so the normal
 * suite never depends on it.
 *
 * <p>Env: {@code JUDGE0_BASE_URL} (default {@code http://localhost:2358}),
 * {@code JUDGE0_API_KEY}, {@code JUDGE0_API_HOST},
 * {@code JUDGE0_PER_PROCESS_LIMITS} (default {@code true}: the local
 * self-hosted Judge0 cannot do cgroup limits, see docs/JUDGE0_POC.md).
 */
class Judge0LiveEngineTest {

    private static JudgeEngine engine;
    private static Problem problem;

    @BeforeAll
    static void connect() {
        String baseUrl = System.getenv().getOrDefault("JUDGE0_BASE_URL", "http://localhost:2358");
        String apiKey = System.getenv("JUDGE0_API_KEY");
        String apiHost = System.getenv("JUDGE0_API_HOST");
        boolean perProcess = Boolean.parseBoolean(
                System.getenv().getOrDefault("JUDGE0_PER_PROCESS_LIMITS", "true"));
        Judge0Client client = new Judge0Client(baseUrl, apiKey, apiHost);
        try {
            client.listLanguages();
        } catch (Judge0Client.Judge0Exception e) {
            assumeTrue(false, "Judge0 not reachable at " + baseUrl + " - skipping live engine tests");
            return;
        }
        Judge0ExecutionService provider =
                new Judge0ExecutionService(client, 250, 120_000, perProcess);
        engine = new JudgeEngine(provider, new OutputComparator(),
                mock(SubmissionTestResultRepository.class),
                new JavaExecutor(), new CppExecutor(), new PythonExecutor());

        problem = new Problem();
        problem.setSlug("sum-two");
        problem.setTitle("Sum Two");
        problem.setStatement("Add them.");
        problem.setInputFormat("Two ints.");
        problem.setOutputFormat("Their sum.");
        problem.setDifficulty(Difficulty.EASY);
        problem.setWeekLabel("Week 1");
        problem.setTimeLimitMs(2000);
        // Roomy address-space cap: this POC env enforces per-process rlimits
        // and a JVM cannot even start under a small `-m` (see JUDGE0_POC.md).
        problem.setMemoryLimitMb(6144);
        TestCase sample = new TestCase();
        sample.setId(1L);
        sample.setInputData("3 4");
        sample.setExpectedOutput("7");
        sample.setSample(true);
        sample.setSortOrder(0);
        TestCase hidden = new TestCase();
        hidden.setId(2L);
        hidden.setInputData("10 20");
        hidden.setExpectedOutput("30");
        hidden.setSample(false);
        hidden.setSortOrder(1);
        problem.addTestCase(sample);
        problem.addTestCase(hidden);
    }

    private Submission judge(Language language, String sourceCode) {
        Submission submission = new Submission();
        submission.setLanguage(language);
        submission.setSourceCode(sourceCode);
        submission.setProblem(problem);
        return engine.judge(submission);
    }

    @Test
    void pythonAcceptedAcrossVisibleAndHidden() {
        Submission judged = judge(Language.PYTHON, "a, b = map(int, input().split())\nprint(a + b)");

        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(judged.getRuntimeMs()).isNotNull();
    }

    @Test
    void javaAccepted() {
        String source = "import java.util.*;\n"
                + "public class Main {\n"
                + "  public static void main(String[] args) {\n"
                + "    Scanner sc = new Scanner(System.in);\n"
                + "    System.out.println(sc.nextLong() + sc.nextLong());\n"
                + "  }\n"
                + "}\n";

        assertThat(judge(Language.JAVA, source).getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void cppAccepted() {
        String source = "#include <bits/stdc++.h>\n"
                + "using namespace std;\n"
                + "int main() {\n"
                + "  long long a, b;\n"
                + "  if (!(cin >> a >> b)) return 0;\n"
                + "  cout << a + b;\n"
                + "  return 0;\n"
                + "}\n";

        assertThat(judge(Language.CPP, source).getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void wrongAnswerWhenOutputDiffers() {
        Submission judged = judge(Language.PYTHON, "print(0)");

        // The verdict comes from OUR comparator over Judge0's stdout; the
        // hidden expected outputs never leave the backend.
        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
    }

    @Test
    void compilationErrorStopsAtFirstTest() {
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
