package com.codingjudge.judge;

public record ExecutionResult(
        String output,
        String error,
        int exitCode,
        long runtimeMs,
        long memoryUsedKb,
        boolean timedOut,
        boolean oomKilled,
        boolean compilationError
) {
    public static ExecutionResult success(String output, long runtimeMs, long memoryUsedKb) {
        return new ExecutionResult(output, "", 0, runtimeMs, memoryUsedKb, false, false, false);
    }

    public static ExecutionResult timeout() {
        return new ExecutionResult("", "", -1, 0, 0, true, false, false);
    }

    public static ExecutionResult oomKilledResult() {
        return new ExecutionResult("", "", -1, 0, 0, false, true, false);
    }

    public static ExecutionResult error(String error, int exitCode) {
        return new ExecutionResult("", error, exitCode, 0, 0, false, false, false);
    }

    public static ExecutionResult compilationError(String error, int exitCode) {
        return new ExecutionResult("", error, exitCode, 0, 0, false, false, true);
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