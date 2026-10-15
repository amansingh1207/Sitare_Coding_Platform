package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.SubmissionTestResult;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionTestResultRepository;
import com.codingjudge.repository.TestCaseRepository;
import com.codingjudge.repository.TestCaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JudgeEngineTest {

    @Mock private DockerSandbox sandbox;
    @Mock private OutputComparator comparator;
    @Mock private SubmissionTestResultRepository testResultRepository;
    @Mock private TestCaseRepository testCaseRepository;
    @Mock private JavaExecutor javaExecutor;
    @Mock private CppExecutor cppExecutor;
    @Mock private PythonExecutor pythonExecutor;

    private JudgeEngine judgeEngine;
    private Submission submission;
    private Problem problem;

    @BeforeEach
    void setUp() {
        judgeEngine = new JudgeEngine(sandbox, comparator, testResultRepository,
                testCaseRepository, javaExecutor, cppExecutor, pythonExecutor);

        submission = new Submission();
        submission.setId(1L);
        submission.setLanguage(Language.JAVA);
        submission.setSourceCode("public class Main {}");

        problem = new Problem();
        problem.setTimeLimitMs(2000);
        problem.setMemoryLimitMb(256);
        submission.setProblem(problem);

        TestCase sampleCase = new TestCase();
        sampleCase.setId(1L);
        sampleCase.setInputData("test input");
        sampleCase.setExpectedOutput("expected output");
        sampleCase.setSample(true);
        sampleCase.setSortOrder(0);

        TestCase hiddenCase = new TestCase();
        hiddenCase.setId(2L);
        hiddenCase.setInputData("hidden input");
        hiddenCase.setExpectedOutput("hidden output");
        hiddenCase.setSample(false);
        hiddenCase.setSortOrder(1);

        problem.addTestCase(sampleCase);
        problem.addTestCase(hiddenCase);
    }

    @Test
    void judge_allTestsPass_returnsAccepted() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.success("expected output", 100, 10240),
                        ExecutionResult.success("hidden output", 100, 10240)));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "hidden output")).thenReturn(true);

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(testResultRepository, times(2)).save(any(SubmissionTestResult.class));
    }

    @Test
    void judge_oneTestFails_returnsWrongAnswer() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.success("expected output", 100, 10240),
                        ExecutionResult.success("wrong output", 100, 10240)));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "wrong output")).thenReturn(false);

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
    }

    @Test
    void judge_withCompilationError_returnsCompilationError() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.compilationError("Compilation failed: class not found", 1),
                        ExecutionResult.compilationError("Compilation failed: class not found", 1)));

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.COMPILATION_ERROR);
    }

    @Test
    void judge_withCompilationError_stopsAfterFirstTest() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.compilationError("Compilation failed: class not found", 1),
                        ExecutionResult.compilationError("Compilation failed: class not found", 1)));

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.COMPILATION_ERROR);
        // Only the first failing test is recorded; the rest are skipped.
        verify(testResultRepository, times(1)).save(any(SubmissionTestResult.class));
    }

    @Test
    void judge_withRuntimeError_returnsRuntimeError() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.error("NullPointerException", -1),
                        ExecutionResult.error("NullPointerException", -1)));

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
    }

    @Test
    void judge_withTimeout_returnsTimeLimitExceeded() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.timeout(),
                        ExecutionResult.timeout()));

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void judge_withOomKilled_returnsMemoryLimitExceeded() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.oomKilledResult(),
                        ExecutionResult.oomKilledResult()));

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);
    }

    @Test
    void judge_withMultipleTestCases_passes() {
        TestCase sample2 = new TestCase();
        sample2.setId(3L);
        sample2.setInputData("input2");
        sample2.setExpectedOutput("output2");
        sample2.setSample(true);
        sample2.setSortOrder(2);
        problem.addTestCase(sample2);

        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.success("expected output", 100, 10240),
                        ExecutionResult.success("hidden output", 100, 10240),
                        ExecutionResult.success("output2", 100, 10240)));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "hidden output")).thenReturn(true);
        when(comparator.compare("output2", "output2")).thenReturn(true);

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(testResultRepository, times(3)).save(any(SubmissionTestResult.class));
    }

    @Test
    void runCustomInput_returnsFirstBatchResult() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(ExecutionResult.success("custom out", 50, 5120)));

        ExecutionResult result =
                judgeEngine.runCustomInput(problem, "public class Main {}", Language.JAVA, "1 2");

        assertThat(result.output()).isEqualTo("custom out");
        assertThat(judgeEngine.executionStatus(result)).isEqualTo(SubmissionStatus.ACCEPTED);
    }

    @Test
    void judge_repeatedSubmissions_work() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(
                        ExecutionResult.success("expected output", 100, 10240),
                        ExecutionResult.success("hidden output", 100, 10240)))
                .thenReturn(List.of(
                        ExecutionResult.success("expected output", 100, 10240),
                        ExecutionResult.success("hidden output", 100, 10240)));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "hidden output")).thenReturn(true);

        Submission result1 = judgeEngine.judge(submission);
        Submission result2 = judgeEngine.judge(submission);

        assertThat(result1.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result2.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(sandbox, times(2)).executeBatch(anyString(), anyList(), any(), anyLong(), anyInt());
    }

    private TestCase caseWith(long id, String input, String expected, boolean sample) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setInputData(input);
        testCase.setExpectedOutput(expected);
        testCase.setSample(sample);
        return testCase;
    }

    private Submission freshSubmission(TestCase... cases) {
        Problem fresh = new Problem();
        fresh.setTimeLimitMs(2000);
        fresh.setMemoryLimitMb(256);
        for (TestCase testCase : cases) {
            fresh.addTestCase(testCase);
        }
        Submission freshSubmission = new Submission();
        freshSubmission.setLanguage(Language.JAVA);
        freshSubmission.setSourceCode("src");
        freshSubmission.setProblem(fresh);
        return freshSubmission;
    }

    private void echoSandbox() {
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenAnswer(invocation -> {
                    List<String> inputs = invocation.getArgument(1);
                    return inputs.stream()
                            .map(in -> ExecutionResult.success("out:" + in, 10, 100))
                            .toList();
                });
        when(comparator.compare(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void judgeStreaming_matchesLegacyJudge() {
        TestCase sample = caseWith(1L, "in-a", "out:in-a", true);
        TestCase hidden = caseWith(2L, "in-b", "out:in-b", false);
        Submission target = freshSubmission(sample, hidden);
        when(testCaseRepository.findIdsByProblemIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(1L, 2L));
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(testCaseRepository.findById(2L)).thenReturn(Optional.of(hidden));
        echoSandbox();

        Submission streamed = judgeEngine.judgeStreaming(target);
        Submission legacy = judgeEngine.judge(target);

        assertThat(streamed.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(legacy.getStatus()).isEqualTo(streamed.getStatus());
        assertThat(legacy.getRuntimeMs()).isEqualTo(streamed.getRuntimeMs());
        assertThat(legacy.getMemoryUsedKb()).isEqualTo(streamed.getMemoryUsedKb());
        ArgumentCaptor<SubmissionTestResult> rows =
                ArgumentCaptor.forClass(SubmissionTestResult.class);
        verify(testResultRepository, times(4)).save(rows.capture());
        assertThat(rows.getAllValues()).extracting(SubmissionTestResult::getActualOutput)
                .containsExactly("out:in-a", "out:in-b", "out:in-a", "out:in-b");
        verify(sandbox, times(3)).executeBatch(anyString(), anyList(), any(), anyLong(), anyInt());
    }

    @Test
    void judgeStreaming_handlesFatHiddenCase() {
        TestCase sample = caseWith(1L, "in-a", "out:in-a", true);
        TestCase hidden = caseWith(2L, "x".repeat(5_000_000), "out:BIG", false);
        Submission target = freshSubmission(sample, hidden);
        when(testCaseRepository.findIdsByProblemIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(1L, 2L));
        when(testCaseRepository.findById(1L)).thenReturn(Optional.of(sample));
        when(testCaseRepository.findById(2L)).thenReturn(Optional.of(hidden));
        echoSandbox();

        Submission result = judgeEngine.judgeStreaming(target);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(testResultRepository, times(2)).save(any(SubmissionTestResult.class));
    }

    @Test
    void judgeStreaming_stopsAfterCompilationError() {
        TestCase sample = caseWith(1L, "in-a", "out:in-a", true);
        TestCase hidden = caseWith(2L, "in-b", "out:in-b", false);
        Submission target = freshSubmission(sample, hidden);
        when(testCaseRepository.findIdsByProblemIdOrderBySortOrderAsc(any()))
                .thenReturn(List.of(1L, 2L));
        when(testCaseRepository.findById(any())).thenReturn(Optional.of(sample));
        when(sandbox.executeBatch(anyString(), anyList(), any(), anyLong(), anyInt()))
                .thenReturn(List.of(ExecutionResult.compilationError("javac boom", 1)));

        Submission result = judgeEngine.judgeStreaming(target);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.COMPILATION_ERROR);
        verify(testResultRepository, times(1)).save(any(SubmissionTestResult.class));
        verify(sandbox, times(1)).executeBatch(anyString(), anyList(), any(), anyLong(), anyInt());
    }

    @Test
    void judgeStreaming_nativeProviderJudgesWholeProblem() {
        TestCase sample = caseWith(1L, "in-a", "out:in-a", true);
        TestCase hidden = caseWith(2L, "in-b", "out:in-b", false);
        Submission target = freshSubmission(sample, hidden);
        when(sandbox.handlesProblemsNatively()).thenReturn(true);
        when(sandbox.judgeTestCases(any(), anyList(), anyString(), any())).thenReturn(List.of(
                ExecutionResult.providerVerdict(SubmissionStatus.ACCEPTED, 5, 100),
                ExecutionResult.providerVerdict(SubmissionStatus.ACCEPTED, 6, 100)));

        Submission result = judgeEngine.judgeStreaming(target);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(sandbox).judgeTestCases(eq(target.getProblem()), eq(List.of(sample, hidden)),
                anyString(), any());
        verify(testResultRepository, times(2)).save(any(SubmissionTestResult.class));
        verify(testCaseRepository, never()).findIdsByProblemIdOrderBySortOrderAsc(any());
    }

    @Test
    void runSamples_usesOnlyProvidedSamples() {
        TestCase sample = caseWith(1L, "in-a", "out:in-a", true);
        echoSandbox();

        JudgeEngine.TestRunSummary summary =
                judgeEngine.runSamples(problem, List.of(sample), "src", Language.JAVA);

        assertThat(summary.status()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(sandbox).executeBatch(anyString(), eq(List.of("in-a")), any(), anyLong(), anyInt());
    }

    @Test
    void runSamples_rejectsEmptySamples() {
        assertThatThrownBy(() -> judgeEngine.runSamples(problem, List.of(), "src", Language.JAVA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
