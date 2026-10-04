package com.codingjudge.judge;

import com.codingjudge.model.entity.Problem;
import com.codingjudge.model.entity.TestCase;

import java.util.List;

/**
 * Execution backend abstraction (Phase 2).
 *
 * <p>The existing Docker judge ({@link DockerSandbox}) is one implementation
 * and keeps working exactly as before. Alternate providers (e.g. Judge0 for
 * production, where no Docker daemon exists) implement this interface and are
 * selected with {@code execution.provider} ({@code EXECUTION_PROVIDER}).
 * {@link JudgeEngine} orchestrates test cases and verdicts on top of this
 * and never talks to Docker directly anymore.
 */
public interface CodeExecutionService {

    /**
     * Run {@code sourceCode} against every input.
     *
     * <p>Semantics mirror {@link DockerSandbox#executeBatch}: one entry per
     * input, in order. Compilation (if the language needs it) is the
     * implementation's business: Docker compiles once per batch, Judge0
     * compiles per call.
     */
    List<ExecutionResult> executeBatch(String sourceCode, List<String> inputs,
                                       LanguageExecutor executor,
                                       long timeoutMs, int memoryLimitMb);

    /**
     * Whether this provider judges whole problems natively (DOMjudge) instead
     * of per-input executions. Default false preserves the per-input path for
     * Docker and Judge0.
     */
    default boolean handlesProblemsNatively() {
        return false;
    }

    /**
     * Judge {@code requested} test cases of {@code problem} in one native
     * submission, returning one result per requested case, in order. Only
     * called when {@link #handlesProblemsNatively()} is true.
     */
    default List<ExecutionResult> judgeTestCases(Problem problem, List<TestCase> requested,
                                                String sourceCode,
                                                LanguageExecutor executor) {
        throw new UnsupportedOperationException("provider judges per-test inputs only");
    }
}
