package com.codingjudge.judge;

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
}
