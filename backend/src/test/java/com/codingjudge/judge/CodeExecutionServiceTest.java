package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionTestResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Proves {@link JudgeEngine} runs on ANY {@link CodeExecutionService}, not
 * just Docker: this fake is deliberately not a {@link DockerSandbox}.
 * (Phase 2 execution abstraction; Docker behavior itself is unchanged and
 * covered by JudgeEngineTest + the Docker-backed suites.)
 */
@ExtendWith(MockitoExtension.class)
class CodeExecutionServiceTest {

    @Mock private SubmissionTestResultRepository testResultRepository;
    @Mock private JavaExecutor javaExecutor;
    @Mock private CppExecutor cppExecutor;
    @Mock private PythonExecutor pythonExecutor;

    /** A Judge0-style provider: one independent result per input. */
    static class FakeProvider implements CodeExecutionService {
        int calls;

        @Override
        public List<ExecutionResult> executeBatch(String sourceCode, List<String> inputs,
                                                 LanguageExecutor executor,
                                                 long timeoutMs, int memoryLimitMb) {
            calls++;
            return inputs.stream()
                    .map(in -> ExecutionResult.success("echo:" + in, 50, 1024))
                    .toList();
        }
    }

    @Test
    void dockerSandboxIsAProvider() {
        assertThat(new DockerSandbox()).isInstanceOf(CodeExecutionService.class);
    }

    @Test
    void engineJudgesThroughNonDockerProvider() {
        FakeProvider provider = new FakeProvider();
        JudgeEngine engine = new JudgeEngine(provider, new OutputComparator(),
                testResultRepository, javaExecutor, cppExecutor, pythonExecutor);

        Problem problem = new Problem();
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitMb(256);
        TestCase first = new TestCase();
        first.setId(1L);
        first.setInputData("a");
        first.setExpectedOutput("echo:a");
        first.setSample(true);
        TestCase second = new TestCase();
        second.setId(2L);
        second.setInputData("b");
        second.setExpectedOutput("echo:b");
        second.setSample(false);
        problem.addTestCase(first);
        problem.addTestCase(second);

        Submission submission = new Submission();
        submission.setLanguage(Language.PYTHON);
        submission.setSourceCode("print(1)");
        submission.setProblem(problem);

        Submission judged = engine.judge(submission);

        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(provider.calls).isEqualTo(1);
        verify(testResultRepository, times(2)).save(any());
    }

    @Test
    void engineHonorsProviderVerdictsWithoutComparing() {
        CodeExecutionService provider = new CodeExecutionService() {
            @Override
            public List<ExecutionResult> executeBatch(String sourceCode, List<String> inputs,
                                                     LanguageExecutor executor,
                                                     long timeoutMs, int memoryLimitMb) {
                // Whole-submission provider (DOMjudge): verdicts arrive final.
                // Outputs are intentionally blank: the API never exposes them.
                return List.of(
                        ExecutionResult.providerVerdict(SubmissionStatus.ACCEPTED, 5, 100),
                        ExecutionResult.providerVerdict(SubmissionStatus.WRONG_ANSWER, 6, 100));
            }
        };
        JudgeEngine engine = new JudgeEngine(provider, new OutputComparator(),
                testResultRepository, javaExecutor, cppExecutor, pythonExecutor);

        Problem problem = new Problem();
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitMb(256);
        TestCase first = new TestCase();
        first.setId(1L);
        first.setInputData("a");
        first.setExpectedOutput("definitely something else");
        first.setSample(true);
        TestCase second = new TestCase();
        second.setId(2L);
        second.setInputData("b");
        second.setExpectedOutput("definitely something else");
        second.setSample(false);
        problem.addTestCase(first);
        problem.addTestCase(second);

        Submission submission = new Submission();
        submission.setLanguage(Language.PYTHON);
        submission.setSourceCode("print(1)");
        submission.setProblem(problem);

        // First case ACCEPTED by provider; second is WRONG_ANSWER even though
        // our own comparator would ALSO say wrong here — the point is the
        // verdict came from the provider flag, not from comparison.
        Submission judged = engine.judge(submission);

        assertThat(judged.getStatus()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        verify(testResultRepository, times(2)).save(any());
    }
}
