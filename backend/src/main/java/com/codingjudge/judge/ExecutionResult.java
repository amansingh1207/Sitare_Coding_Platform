package com.codingjudge.judge;

import com.codingjudge.model.enums.SubmissionStatus;

public record ExecutionResult(
        String output,
        String error,
        int exitCode,
        long runtimeMs,
        long memoryUsedKb,
        boolean timedOut,
        boolean oomKilled,
        boolean compilationError,
        SubmissionStatus providerVerdict,
        boolean unsupported
) {
    public static ExecutionResult success(String output, long runtimeMs, long memoryUsedKb) {
        return new ExecutionResult(output, "", 0, runtimeMs, memoryUsedKb, false, false, false, null, false);
    }

    public static ExecutionResult timeout() {
        return new ExecutionResult("", "", -1, 0, 0, true, false, false, null, false);
    }

    public static ExecutionResult oomKilledResult() {
        return new ExecutionResult("", "", -1, 0, 0, false, true, false, null, false);
    }

    /**
     * Out-of-memory detected from the program's own error output.
     * A JVM or CPython runtime reports OOM itself and exits non-zero (often 1),
     * so the exit code alone cannot distinguish it from an ordinary crash.
     */
    public static ExecutionResult oomKilledWithOutput(String output, int exitCode) {
        return new ExecutionResult("", output, exitCode, 0, 0, false, true, false, null, false);
    }

    public static ExecutionResult error(String error, int exitCode) {
        return new ExecutionResult("", error, exitCode, 0, 0, false, false, false, null, false);
    }

    public static ExecutionResult compilationError(String error, int exitCode) {
        return new ExecutionResult("", error, exitCode, 0, 0, false, false, true, null, false);
    }

    /**
     * The provider cannot perform this kind of execution at all (e.g.
     * arbitrary-stdin runs on DOMjudge). Unlike {@link #error}, this is not a
     * program failure — callers must surface it as "unavailable" guidance,
     * never as a crash verdict.
     */
    public static ExecutionResult unsupported(String error) {
        return new ExecutionResult("", error, -1, 0, 0, false, false, false, null, true);
    }

    /**
     * A per-test verdict decided by the execution provider itself (DOMjudge).
     * Used when the provider judges whole submissions and the raw program
     * output is not available for our own comparison. {@link
     * com.codingjudge.judge.JudgeEngine} honors it verbatim; all other
     * providers leave it null and go through output comparison.
     */
    public static ExecutionResult providerVerdict(SubmissionStatus status, long runtimeMs,
                                                  long memoryUsedKb) {
        return new ExecutionResult("", "", 0, runtimeMs, memoryUsedKb,
                false, false, false, status, false);
    }

    public boolean hasProviderVerdict() {
        return providerVerdict != null;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isOomKilled() {
        return oomKilled;
    }

    public boolean isCompilationError() {
        return compilationError;
    }

    public boolean isUnsupported() {
        return unsupported;
    }
}