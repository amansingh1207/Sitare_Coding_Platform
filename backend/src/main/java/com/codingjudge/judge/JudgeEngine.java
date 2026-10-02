package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
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

    public JudgeEngine(DockerSandbox sandbox,
                       OutputComparator comparator,
                       SubmissionTestResultRepository testResultRepository,
                       JavaExecutor javaExecutor,
                       CppExecutor cppExecutor) {
        this.sandbox = sandbox;
        this.comparator = comparator;
        this.testResultRepository = testResultRepository;
        this.javaExecutor = javaExecutor;
        this.cppExecutor = cppExecutor;
    }

    public Submission judge(Submission submission) {
        Language language = submission.getLanguage();
        LanguageExecutor executor = getExecutor(language);
        
        long timeoutMs = submission.getProblem().getTimeLimitMs();
        int memoryLimitMb = submission.getProblem().getMemoryLimitMb();

        // Run compilation + all test cases via execute (which handles compilation internally)
        List<TestCase> testCases = submission.getProblem().getTestCases();
        boolean allAccepted = true;
        
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
            }
        }
        
        submission.setStatus(allAccepted ? SubmissionStatus.ACCEPTED : SubmissionStatus.WRONG_ANSWER);
        return submission;
    }
    
    private SubmissionStatus determineTestStatus(ExecutionResult result, String expectedOutput) {
        if (result.timedOut()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (result.isOomKilled()) {
            return SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        }
        if (result.exitCode() != 0) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        
        if (OutputComparator.compare(expectedOutput, result.output())) {
            return SubmissionStatus.ACCEPTED;
        }
        return SubmissionStatus.WRONG_ANSWER;
    }

    private LanguageExecutor getExecutor(Language language) {
        return switch (language) {
            case JAVA -> javaExecutor;
            case CPP -> cppExecutor;
            case PYTHON -> throw new UnsupportedOperationException("Python not yet implemented");
        };
    }
}