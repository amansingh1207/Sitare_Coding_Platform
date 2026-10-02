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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JudgeEngineTest {

    @Mock private DockerSandbox sandbox;
    @Mock private OutputComparator comparator;
    @Mock private SubmissionTestResultRepository testResultRepository;
    @Mock private JavaExecutor javaExecutor;
    @Mock private CppExecutor cppExecutor;
    @Mock private PythonExecutor pythonExecutor;

    private JudgeEngine judgeEngine;
    private Submission submission;
    private Problem problem;

    @BeforeEach
    void setUp() {
        judgeEngine = new JudgeEngine(sandbox, comparator, testResultRepository,
                javaExecutor, cppExecutor, pythonExecutor);

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
        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.success("expected output", 100, 10240))
                .thenReturn(ExecutionResult.success("hidden output", 100, 10240));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "hidden output")).thenReturn(true);

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(testResultRepository, times(2)).save(any(SubmissionTestResult.class));
    }

    @Test
    void judge_oneTestFails_returnsWrongAnswer() {
        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.success("expected output", 100, 10240))
                .thenReturn(ExecutionResult.success("wrong output", 100, 10240));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "wrong output")).thenReturn(false);

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
    }

    @Test
    void judge_withCompilationError_returnsCompilationError() {
        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.compilationError("Compilation failed: class not found", 1));

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.COMPILATION_ERROR);
    }

    @Test
    void judge_withRuntimeError_returnsRuntimeError() {
        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.error("NullPointerException", -1));

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
    }

    @Test
    void judge_withTimeout_returnsTimeLimitExceeded() {
        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.timeout());

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void judge_withOomKilled_returnsMemoryLimitExceeded() {
        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.oomKilledResult());

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

        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.success("expected output", 100, 10240))
                .thenReturn(ExecutionResult.success("hidden output", 100, 10240))
                .thenReturn(ExecutionResult.success("output2", 100, 10240));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "hidden output")).thenReturn(true);
        when(comparator.compare("output2", "output2")).thenReturn(true);

        Submission result = judgeEngine.judge(submission);

        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(testResultRepository, times(3)).save(any(SubmissionTestResult.class));
    }

    @Test
    void judge_repeatedSubmissions_work() {
        when(sandbox.execute(anyString(), anyString(), any(), anyLong(), anyInt()))
                .thenReturn(ExecutionResult.success("expected output", 100, 10240))
                .thenReturn(ExecutionResult.success("hidden output", 100, 10240))
                .thenReturn(ExecutionResult.success("expected output", 100, 10240))
                .thenReturn(ExecutionResult.success("hidden output", 100, 10240));
        when(comparator.compare("expected output", "expected output")).thenReturn(true);
        when(comparator.compare("hidden output", "hidden output")).thenReturn(true);

        Submission result1 = judgeEngine.judge(submission);
        Submission result2 = judgeEngine.judge(submission);

        assertThat(result1.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(result2.getStatus()).isEqualTo(SubmissionStatus.ACCEPTED);
        verify(sandbox, times(4)).execute(anyString(), anyString(), any(), anyLong(), anyInt());
    }
}
