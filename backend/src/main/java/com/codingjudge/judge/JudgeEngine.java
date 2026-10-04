package com.codingjudge.judge;

import com.codingjudge.judge.executor.CppExecutor;
import com.codingjudge.judge.executor.JavaExecutor;
import com.codingjudge.judge.executor.PythonExecutor;
import com.codingjudge.model.dto.response.SubmissionTestResultResponse;
import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.Submission;
import com.codingjudge.model.entity.SubmissionTestResult;
import com.codingjudge.model.entity.TestCase;
import com.codingjudge.model.enums.Language;
import com.codingjudge.model.enums.SubmissionStatus;
import com.codingjudge.repository.SubmissionTestResultRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class JudgeEngine {

    private final CodeExecutionService sandbox;
    private final OutputComparator comparator;
    private final SubmissionTestResultRepository testResultRepository;
    private final JavaExecutor javaExecutor;
    private final CppExecutor cppExecutor;
    private final PythonExecutor pythonExecutor;

    public JudgeEngine(CodeExecutionService sandbox,
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

        // Compilation + every test case, folded into one verdict.
        TestRunSummary summary = executeAgainst(
                submission.getProblem().getTestCases(),
                submission.getSourceCode(),
                executor,
                timeoutMs,
                memoryLimitMb);

        for (TestOutcome outcome : summary.outcomes()) {
            SubmissionTestResult result = new SubmissionTestResult();
            result.setSubmission(submission);
            result.setTestCase(outcome.testCase());
            result.setRuntimeMs((int) Math.min(outcome.runtimeMs(), Integer.MAX_VALUE));
            result.setMemoryUsedKb((int) Math.min(outcome.memoryUsedKb(), Integer.MAX_VALUE));
            result.setStatus(outcome.status());
            result.setActualOutput(outcome.actualOutput());
            testResultRepository.save(result);
        }

        submission.setStatus(summary.status());
        if (summary.maxRuntimeMs() > 0) {
            submission.setRuntimeMs((int) Math.min(summary.maxRuntimeMs(), Integer.MAX_VALUE));
        }
        if (summary.maxMemoryKb() > 0) {
            submission.setMemoryUsedKb((int) Math.min(summary.maxMemoryKb(), Integer.MAX_VALUE));
        }
        submission.setJudgedAt(Instant.now());
        return submission;
    }

    /**
     * Runs code against a problem's sample test cases only, persisting nothing.
     *
     * This backs the editor's "Run" button. Hidden test cases are never touched,
     * so running can never reveal them, and no Submission row is created.
     */
    public TestRunSummary runSamples(Problem problem, String sourceCode, Language language) {
        List<TestCase> samples = problem.getTestCases().stream()
                .filter(testCase -> Boolean.TRUE.equals(testCase.getSample()))
                .toList();
        if (samples.isEmpty()) {
            throw new IllegalArgumentException(
                    "Problem '" + problem.getSlug() + "' has no sample test cases to run against");
        }
        return executeAgainst(
                samples,
                sourceCode,
                getExecutor(language),
                problem.getTimeLimitMs(),
                problem.getMemoryLimitMb());
    }

    /**
     * Runs code against one custom stdin without comparing to any expected
     * output and without persisting anything. Used by the editor's
     * "custom input" feature for students, professors and admins alike.
     */
    public ExecutionResult runCustomInput(Problem problem, String sourceCode,
                                          Language language, String stdin) {
        List<ExecutionResult> results = sandbox.executeBatch(
                sourceCode,
                stdin == null ? List.of("") : List.of(stdin),
                getExecutor(language),
                problem.getTimeLimitMs(),
                problem.getMemoryLimitMb());
        return results.isEmpty()
                ? ExecutionResult.error("Execution failed: no result produced", -1)
                : results.get(0);
    }

    /** Executes the source once per test case and folds the results into a verdict. */
    private TestRunSummary executeAgainst(List<TestCase> testCases,
                                          String sourceCode,
                                          LanguageExecutor executor,
                                          long timeoutMs,
                                          int memoryLimitMb) {
        boolean allAccepted = true;
        SubmissionStatus verdict = null;
        long maxRuntimeMs = 0;
        long maxMemoryKb = 0;
        List<TestOutcome> outcomes = new ArrayList<>(testCases.size());

        // One call per batch; batching granularity is the provider's business
        // (Docker: one container + one compilation, Judge0: one call per test).
        // Whole-problem providers (DOMjudge) judge the requested cases
        // natively instead; the loop below stays identical either way.
        List<ExecutionResult> execResults;
        if (sandbox.handlesProblemsNatively() && !testCases.isEmpty()) {
            Problem problem = testCases.get(0).getProblem();
            execResults = sandbox.judgeTestCases(problem, testCases, sourceCode, executor);
        } else {
            List<String> inputs = testCases.stream().map(TestCase::getInputData).toList();
            execResults = sandbox.executeBatch(
                    sourceCode, inputs, executor, timeoutMs, memoryLimitMb);
        }

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            ExecutionResult execResult = execResults.get(i);

            // Providers that judge whole submissions themselves (DOMjudge)
            // attach the final per-test verdict; everyone else goes through
            // output comparison below. Either way the loop, persistence and
            // worst-verdict folding stay identical.
            SubmissionStatus testStatus = execResult.hasProviderVerdict()
                    ? execResult.providerVerdict()
                    : determineTestStatus(execResult, testCase.getExpectedOutput());
            outcomes.add(new TestOutcome(
                    testCase,
                    testStatus,
                    visibleOutput(execResult),
                    execResult.runtimeMs(),
                    execResult.memoryUsedKb()));

            // Metrics report the worst case across all test cases, which is what
            // a student cares about when tuning limits.
            maxRuntimeMs = Math.max(maxRuntimeMs, execResult.runtimeMs());
            maxMemoryKb = Math.max(maxMemoryKb, execResult.memoryUsedKb());

            if (testStatus != SubmissionStatus.ACCEPTED) {
                allAccepted = false;
                // Keep the most specific failure reason (docs/JUDGE_DESIGN.md section 9).
                verdict = worseOf(verdict, testStatus);
            }

            // A compilation failure poisons every test identically: the binary
            // was never produced, so running the remaining inputs can only
            // repeat the same verdict while burning container time.
            if (testStatus == SubmissionStatus.COMPILATION_ERROR) {
                break;
            }
        }

        return new TestRunSummary(
                outcomes,
                allAccepted ? SubmissionStatus.ACCEPTED : verdict,
                maxRuntimeMs,
                maxMemoryKb);
    }

    /**
     * The output a student is allowed to see.
     *
     * Compiler diagnostics and runtime stack traces arrive on the error stream
     * rather than stdout, so falling back to it is what makes a failed
     * submission actionable instead of an empty output box.
     */
    private String visibleOutput(ExecutionResult result) {
        String output = result.output();
        if (output != null && !output.isBlank()) {
            return output;
        }
        String error = result.error();
        return error == null ? output : error;
    }

    /** Per-test-case outcome, before it is either persisted or returned. */
    public record TestOutcome(TestCase testCase,
                              SubmissionStatus status,
                              String actualOutput,
                              long runtimeMs,
                              long memoryUsedKb) {
    }

    /** Aggregate verdict for a set of executed test cases. */
    public record TestRunSummary(List<TestOutcome> outcomes,
                                 SubmissionStatus status,
                                 long maxRuntimeMs,
                                 long maxMemoryKb) {
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
    
    /**
     * Status of a raw execution with no expected output to compare against,
     * e.g. a custom-input run. A clean exit counts as ACCEPTED: the program
     * ran, and what it printed is shown to the user verbatim.
     */
    public SubmissionStatus executionStatus(ExecutionResult result) {
        if (result.isCompilationError()) {
            return SubmissionStatus.COMPILATION_ERROR;
        }
        if (result.isTimedOut()) {
            return SubmissionStatus.TIME_LIMIT_EXCEEDED;
        }
        if (result.isOomKilled()) {
            return SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        }
        // 128 + SIGKILL(9) is how the OOM killer terminates the process.
        if (result.exitCode() == 137) {
            return SubmissionStatus.MEMORY_LIMIT_EXCEEDED;
        }
        if (result.exitCode() != 0) {
            return SubmissionStatus.RUNTIME_ERROR;
        }
        return SubmissionStatus.ACCEPTED;
    }

    private SubmissionStatus determineTestStatus(ExecutionResult result, String expectedOutput) {
        SubmissionStatus execution = executionStatus(result);
        if (execution != SubmissionStatus.ACCEPTED) {
            return execution;
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