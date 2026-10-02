package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.dto.response.SubmissionTestResultResponse;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.SubmissionTestResult;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionTestResultRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JudgeEngine {

    private final DockerSandbox sandbox;
    private final OutputComparator comparator;
    private final SubmissionTestResultRepository testResultRepository;
    private final JavaExecutor javaExecutor;
    private final CppExecutor cppExecutor;
    private final PythonExecutor pythonExecutor;

    public JudgeEngine(DockerSandbox sandbox,
                       OutputComparator comparator,
                       SubmissionTestResultRepository testResultRepository,
                       JavaExecutor javaExecutor,
                       CppExecutor cppExecutor,
                       PythonExecutor pythonExecutor) {
        this.sandbox = sandbox;
        this.comparator = comparator;
        this.testResultRepository = testResultRepository;
        this.javaExecutor = javaExecutor;
        this.cppExecutor = cppExecutor;
        this.pythonExecutor = pythonExecutor;
    }

    public Submission judge(Submission submission) {
        Language language = submission.getLanguage();
        LanguageExecutor executor = getExecutor(language);
        
        long timeoutMs = submission.getProblem().getTimeLimitMs();
        int memoryLimitMb = submission.getProblem().getMemoryLimitMb();

        // Run compilation + all test cases via execute (which handles compilation internally)
        List<TestCase> testCases = submission.getProblem().getTestCases();
        boolean allAccepted = true;
        SubmissionStatus verdict = null;
        
        for (TestCase testCase : testCases) {
            ExecutionResult execResult = sandbox.execute(
                    submission.getSourceCode(),
                    testCase.getInputData(),
                    executor,
                    timeoutMs,
                    memoryLimitMb
            );
            
            SubmissionTestResult result = new SubmissionTestResult();
            result.setSubmission(submission);
            result.setTestCase(testCase);
            result.setRuntimeMs((int) execResult.runtimeMs());
            result.setMemoryUsedKb((int) execResult.memoryUsedKb());
            
            SubmissionStatus testStatus = determineTestStatus(execResult, testCase.getExpectedOutput());
            result.setStatus(testStatus);
            result.setActualOutput(execResult.output());
            
            testResultRepository.save(result);
            
            if (testStatus != SubmissionStatus.ACCEPTED) {
                allAccepted = false;
                // Keep the most specific failure reason (docs/JUDGE_DESIGN.md section 9).
                verdict = worseOf(verdict, testStatus);
            }
        }
        
        submission.setStatus(allAccepted ? SubmissionStatus.ACCEPTED : verdict);
        return submission;
    }

    /**
     * Severity order for a submission verdict. A compilation failure is more
     * fundamental than a limit breach, which is more fundamental than a crash,
     * which is more fundamental than a plain wrong answer.
     */
    private SubmissionStatus worseOf(SubmissionStatus current, SubmissionStatus candidate) {
        if (current == null) {
            return candidate;
        }
        return severity(candidate) > severity(current) ? candidate : current;
    }

    private int severity(SubmissionStatus status) {
        return switch (status) {
            case WRONG_ANSWER -> 1;
            case RUNTIME_ERROR -> 2;
            case MEMORY_LIMIT_EXCEEDED -> 3;
            case TIME_LIMIT_EXCEEDED -> 4;
            case COMPILATION_ERROR -> 5;
            case INTERNAL_ERROR -> 6;
            default -> 0;
        };
    }
    
    private SubmissionStatus determineTestStatus(ExecutionResult result, String expectedOutput) {
        if (result.isCompilationError()) {
            return SubmissionStatus.COMPILATION_ERROR;
        }
        if (result.isTimedOut()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (result.isOomKilled()) {
            return SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        }
        if (result.exitCode() != 0) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        
        if (comparator.compare(expectedOutput, result.output())) {
            return SubmissionStatus.ACCEPTED;
        }
        return SubmissionStatus.WRONG_ANSWER;
    }

    private LanguageExecutor getExecutor(Language language) {
        return switch (language) {
            case JAVA -> javaExecutor;
            case CPP -> cppExecutor;
            case PYTHON -> pythonExecutor;
        };
    }
}