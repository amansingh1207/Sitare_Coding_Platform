package com.codingjudge.judge;

public record CompilationResult(
        boolean success,
        String output,
        int exitCode
) {
    public static CompilationResult success(String output) {
        return new CompilationResult(true, output, 0);
    }

    public static CompilationResult failure(String output, int exitCode) {
        return new CompilationResult(false, output, exitCode);
    }

    public boolean isSuccess() {
        return success;
    }
}