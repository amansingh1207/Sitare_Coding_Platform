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
        SubmissionStatus providerVerdict
) {
    public static ExecutionResult success(String output, long runtimeMs, long memoryUsedKb) {
        return new ExecutionResult(output, "", 0, runtimeMs, memoryUsedKb, false, false, false, null);
    }

    public static ExecutionResult timeout() {
        return new ExecutionResult("", "", -1, 0, 0, true, false, false, null);
    }

    public static ExecutionResult oomKilledResult() {
        return new ExecutionResult("", "", -1, 0, 0, false, true, false, null);
    }

    /**
     * Out-of-memory detected from the program's own error output.
     * A JVM or CPython runtime reports OOM itself and exits non-zero (often 1),
     * so the exit code alone cannot distinguish it from an ordinary crash.
     */
    public static ExecutionResult oomKilledWithOutput(String output, int exitCode) {
        return new ExecutionResult("", output, exitCode, 0, 0, false, true, false, null);
    }

    public static ExecutionResult error(String error, int exitCode) {
        return new ExecutionResult("", error, exitCode, 0, 0, false, false, false, null);
    }

    public static ExecutionResult compilationError(String error, int exitCode) {
        return new ExecutionResult("", error, exitCode, 0, 0, false, false, true, null);
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
                false, false, false, status);
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
}